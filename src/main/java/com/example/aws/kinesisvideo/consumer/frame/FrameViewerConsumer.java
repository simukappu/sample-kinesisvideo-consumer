package com.example.aws.kinesisvideo.consumer.frame;

import java.awt.image.BufferedImage;

import com.amazonaws.kinesisvideo.parser.examples.KinesisVideoFrameViewer;
import com.example.aws.kinesisvideo.base.AbstractFrameExtractorConsumer;
import com.example.aws.kinesisvideo.base.AbstractKinesisVideoConsumer;

public class FrameViewerConsumer extends AbstractFrameExtractorConsumer {

	private static final int VIEWER_WIDTH = Integer.parseInt(System.getProperty("viewer.width", "1280"));
	private static final int VIEWER_HEIGHT = Integer.parseInt(System.getProperty("viewer.height", "720"));

	private KinesisVideoFrameViewer kinesisVideoFrameViewer;

	public FrameViewerConsumer() {
		super();
		kinesisVideoFrameViewer = new KinesisVideoFrameViewer(VIEWER_WIDTH, VIEWER_HEIGHT);
		kinesisVideoFrameViewer.setVisible(true);
	}

	@Override
	protected void process(BufferedImage imageFrame) {
		kinesisVideoFrameViewer.update(imageFrame);
	}

	@Override
	protected void quit() {
	}

	public static void main(String[] args) {
		AbstractKinesisVideoConsumer consumer = new FrameViewerConsumer();
		consumer.getMediaLoop();
	}

}
