package com.example.aws.kinesisvideo.consumer.frame;

import static org.bytedeco.javacpp.opencv_imgproc.rectangle;

import java.awt.image.BufferedImage;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.RectVector;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_objdetect.CascadeClassifier;
import org.bytedeco.javacv.Java2DFrameUtils;

import com.amazonaws.kinesisvideo.parser.examples.KinesisVideoFrameViewer;
import com.example.aws.kinesisvideo.base.AbstractFrameExtractorConsumer;
import com.example.aws.kinesisvideo.base.AbstractKinesisVideoConsumer;

public class OpenCVFrameProcessorConsumer extends AbstractFrameExtractorConsumer {

	private static final int VIEWER_WIDTH = Integer.parseInt(System.getProperty("viewer.width", "1280"));
	private static final int VIEWER_HEIGHT = Integer.parseInt(System.getProperty("viewer.height", "720"));

	private CascadeClassifier faceDetector;
	private KinesisVideoFrameViewer kinesisVideoFrameViewer;

	public OpenCVFrameProcessorConsumer() {
		super();
		try {
			String classifierName = Paths
					.get(OpenCVFrameProcessorConsumer.class.getResource("/haarcascade_frontalface_default.xml").toURI()).toString();
			faceDetector = new CascadeClassifier(classifierName);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		kinesisVideoFrameViewer = new KinesisVideoFrameViewer(VIEWER_WIDTH, VIEWER_HEIGHT);
		kinesisVideoFrameViewer.setVisible(true);
	}

	@Override
	protected void process(BufferedImage imageFrame) {
		// Convert BufferedImage to Mat
		Mat source = Java2DFrameUtils.toMat(imageFrame);

		// Find the faces in the frame by Haar cascade classifier
		RectVector faces = new RectVector();
		faceDetector.detectMultiScale(source, faces);
		long numOfFaces = faces.size();
		LOG.info(numOfFaces + " faces are detected!");

		// Render detected face rectangle to image
		for (int i = 0; i < numOfFaces; i++) {
			Rect r = faces.get(i);
			int x = r.x(), y = r.y(), h = r.height(), w = r.width();
			LOG.info(" id=" + (i + 1) + ", x=" + x + ", y=" + y + ", h=" + h + ", w=" + w);
			rectangle(source, r, new Scalar(0, 0, 255, 0));
		}

		// Show in viewer
		BufferedImage outputImage = Java2DFrameUtils.toBufferedImage(source);
		kinesisVideoFrameViewer.update(outputImage);
	}

	@Override
	protected void quit() {
	}

	public static void main(String[] args) {
		AbstractKinesisVideoConsumer consumer = new OpenCVFrameProcessorConsumer();
		consumer.getMediaLoop();
	}

}
