package com.example.aws.kinesisvideo.base;

import java.awt.image.BufferedImage;
import java.util.Optional;

import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.mkv.visitors.CompositeMkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.example.aws.kinesisvideo.parser.ParsingVisitor;

public abstract class AbstractFrameExtractorConsumer extends AbstractKinesisVideoConsumer {

	private static final int SKIP_FRAME_COUNT = Integer.parseInt(System.getProperty("skip.frame.count", "0"));

	private int skipedFrameCount = 0;

	protected abstract void process(BufferedImage frameImage);

	@Override
	protected void consume(GetMediaResult result) {
		// Fragment metadata visitor to extract Kinesis Video fragment metadata from the GetMedia stream.
		FragmentMetadataVisitor fragmentMetadataVisitor = FragmentMetadataVisitor.create();
		ParsingVisitor parsingVisitor = new ParsingVisitor();
		MkvElementVisitor elementVisitor = new CompositeMkvElementVisitor(fragmentMetadataVisitor, parsingVisitor);
		StreamingMkvReader mkvStreamReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(result.getPayload()));

		// Apply the OutputSegmentMerger to the mkv elements from the mkv stream reader.
		try {
			while (mkvStreamReader.mightHaveNext()) {
				Optional<MkvElement> mkvElementOptional = mkvStreamReader.nextIfAvailable();
				if (mkvElementOptional.isPresent()) {
					MkvElement mkvElement = mkvElementOptional.get();
					// Apply the parsing visitor to this element through the element visitor.
					mkvElement.accept(elementVisitor);
					// Process the extracted image frame.
					BufferedImage imageFrame = parsingVisitor.getImageFrame();
					if (imageFrame != null) {
						if (skipedFrameCount > SKIP_FRAME_COUNT) {
							process(imageFrame);
							skipedFrameCount = 0;
						}
					}
					skipedFrameCount++;
				}
			}
		} catch (MkvElementVisitException e) {
			LOG.error("Exception from visitor to MkvElement " + e);
		}
	}

}
