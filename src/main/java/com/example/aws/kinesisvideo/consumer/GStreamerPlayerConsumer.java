package com.example.aws.kinesisvideo.consumer;

import java.awt.EventQueue;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.elements.PlayBin;

import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.OutputSegmentMerger;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.example.aws.kinesisvideo.AbstractKinesisVideoConsumer;

public class GStreamerPlayerConsumer extends AbstractKinesisVideoConsumer {

	private static final String OUTPUT_FILENAME = System.getProperty("output.filename", "kinesisvideo_output.mkv");
	private static final boolean IF_SAVE_OUTPUT_FILE = Boolean.parseBoolean(System.getProperty("if.save.output.file", "true"));
	private static PlayBin PLAY_BIN;

	@Override
	protected void consume(GetMediaResult result) {
		Gst.init();
		Path outputPath = Paths.get(OUTPUT_FILENAME);
		LOG.info("Writing merged video file to " + outputPath.toAbsolutePath().toString());
		try {
			OutputStream fileOutputStream = Files.newOutputStream(outputPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
			try (BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream)) {
				outputStream.flush();

				StreamingMkvReader mkvStreamReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(result.getPayload()));
				OutputSegmentMerger outputSegmentMerger = OutputSegmentMerger.createDefault(fileOutputStream);

				long prevFragmentDoneTime = System.currentTimeMillis();
				// Apply the OutputSegmentMerger to the mkv elements from the mkv stream reader.
				try {
					while (mkvStreamReader.mightHaveNext()) {
						Optional<MkvElement> mkvElementOptional = mkvStreamReader.nextIfAvailable();
						if (mkvElementOptional.isPresent()) {
							MkvElement mkvElement = mkvElementOptional.get();
							// Apply the segment merger to this element.
							mkvElement.accept(outputSegmentMerger);

							// Count the number of fragments merged.
							if (MkvTypeInfos.SEGMENT.equals(mkvElement.getElementMetaData().getTypeInfo())) {
								if (mkvElement instanceof MkvEndMasterElement) {
									LOG.info("Fragment numbers merged " + outputSegmentMerger.getClustersCount() + " took  "
											+ Long.toString(System.currentTimeMillis() - prevFragmentDoneTime) + " ms.");
									prevFragmentDoneTime = System.currentTimeMillis();
									outputStream.flush();

									// Start GStreamer player.
									if (outputSegmentMerger.getClustersCount() == 1) {
										EventQueue.invokeLater(() -> {
											PLAY_BIN = new PlayBin("playbin");
											// Set ShutdownHook to stop GStreamer player.
											Runtime.getRuntime().addShutdownHook(new Thread(() -> {
												quit();
											}));
											PLAY_BIN.setName(getStreamName());
											PLAY_BIN.setURI((new File(OUTPUT_FILENAME)).toURI());
											PLAY_BIN.play();
										});
									}
								}
							}
						}
					}
				} catch (MkvElementVisitException e) {
					LOG.error("Exception from visitor to MkvElement " + e);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void quit() {
		if (PLAY_BIN != null) {
			PLAY_BIN.stop();
		}
		if (!IF_SAVE_OUTPUT_FILE) {
			File outputFile = new File(OUTPUT_FILENAME);
			if (outputFile.exists()) {
				outputFile.delete();
			}
		}
	}

	public static void main(String[] args) {
		AbstractKinesisVideoConsumer consumer = new GStreamerPlayerConsumer();
		consumer.getMediaLoop();
	}
}
