package com.example.aws.kinesisvideo.consumer.frame;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.example.aws.kinesisvideo.base.AbstractFrameExtractorConsumer;
import com.example.aws.kinesisvideo.base.AbstractKinesisVideoConsumer;

public class FrameFileStoreConsumer extends AbstractFrameExtractorConsumer {

	private static final String FRAME_DIRECATORY = System.getProperty("frame.directory", "image");
	private static final String FRAME_FILE_PREFIX = System.getProperty("frame.file.prefix", "frame-");
	// jpg, jpeg, bmp, png etc.
	private static final String FRAME_FILE_FORMAT = System.getProperty("frame.file.format", "jpg");
	private long frameCount = 1;

	public FrameFileStoreConsumer() {
		super();
		File directory = new File(FRAME_DIRECATORY);
		if (!directory.exists()) {
			directory.mkdirs();
		}
	}

	@Override
	protected void process(BufferedImage imageFrame) {
		String fileName = FRAME_DIRECATORY + "/" + FRAME_FILE_PREFIX + frameCount + "." + FRAME_FILE_FORMAT;
		File outputfile = new File(fileName);
		try {
			ImageIO.write(imageFrame, FRAME_FILE_FORMAT, outputfile);
			LOG.info("Frame saved: " + fileName);
			frameCount++;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void quit() {
	}

	public static void main(String[] args) {
		AbstractKinesisVideoConsumer consumer = new FrameFileStoreConsumer();
		consumer.getMediaLoop();
	}

}
