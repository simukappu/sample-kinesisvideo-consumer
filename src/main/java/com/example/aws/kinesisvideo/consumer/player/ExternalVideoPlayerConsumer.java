package com.example.aws.kinesisvideo.consumer.player;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.utilities.OutputSegmentMerger;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.example.aws.kinesisvideo.base.AbstractKinesisVideoConsumer;

public class ExternalVideoPlayerConsumer extends AbstractKinesisVideoConsumer {

	private static final String OUTPUT_FILENAME = System.getProperty("output.filename", "kinesisvideo_output.mkv");
	// "gst-play-1.0 <output.filename>", "open -a 5KPlayer <output.filename>" and so on
	private static final String RUN_PLAYER_COMMAND = System.getProperty("run.player.command", "open -a 5KPlayer " + OUTPUT_FILENAME);
	// "killall gst-play-1.0", "killall 5KPlayer" and so on
	private static final String QUIT_PLAYER_COMMAND = System.getProperty("quit.player.command", "killall 5KPlayer");
	private static final boolean IF_SAVE_OUTPUT_FILE = Boolean.parseBoolean(System.getProperty("if.save.output.file", "true"));

	@Override
	protected void consume(GetMediaResult result) {
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

									// Start external video player.
									if (outputSegmentMerger.getClustersCount() == 1) {
										// Set ShutdownHook to quit external video player.
										Runtime.getRuntime().addShutdownHook(new Thread(() -> {
											quit();
										}));
										Runtime.getRuntime().exec(RUN_PLAYER_COMMAND);
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
		try {
			Runtime.getRuntime().exec(QUIT_PLAYER_COMMAND);
			if (!IF_SAVE_OUTPUT_FILE) {
				File outputFile = new File(OUTPUT_FILENAME);
				if (outputFile.exists()) {
					outputFile.delete();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		AbstractKinesisVideoConsumer consumer = new ExternalVideoPlayerConsumer();
		consumer.getMediaLoop();
	}
}
