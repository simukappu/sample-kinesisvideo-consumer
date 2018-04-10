package com.example.aws.kinesisvideo.consumer.frame;

import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_videoio.VideoWriter;
import org.bytedeco.javacv.Java2DFrameUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.example.aws.kinesisvideo.base.AbstractFrameExtractorWithTimestampConsumer;
import com.example.aws.kinesisvideo.base.AbstractKinesisVideoConsumer;

public class OpencvVideoWriterConsumer extends AbstractFrameExtractorWithTimestampConsumer {

	private static final String VIDEO_DIRECATORY = (System.getProperty("video.directory", "video"));
	private static final String VIDEO_FILENAME_PREFIX = (System.getProperty("video.filename.prefix", "kinesisvideo_opencv_"));
	private static final int VIDEO_FPS = Integer.parseInt(System.getProperty("video.fps", "25"));
	private static final int VIDEO_WIDTH = Integer.parseInt(System.getProperty("video.width", "1280"));
	private static final int VIDEO_HEIGHT = Integer.parseInt(System.getProperty("video.height", "720"));
	private static final int VIDEO_FILE_INTERVAL_SECONDS = Integer.parseInt(System.getProperty("video.file.interval.seconds", "10"));
	private static final long VIDEO_FILE_INTERVAL_MILLIS = VIDEO_FILE_INTERVAL_SECONDS * 1000L;

	private static final DateTimeFormatter LOCAL_DTF = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneId.of("UTC"));
	private static final DateTimeFormatter S3_DTF = DateTimeFormatter.ofPattern("uuuu/MM/dd/HHmmss").withZone(ZoneId.of("UTC"));

	private static final boolean IF_SAVE_VIDEO_FILE_S3 = Boolean.parseBoolean(System.getProperty("if.save.video.file.s3", "false"));
	private static final String S3_BUCKET_NAME = (System.getProperty("s3.bucket.name"));
	private static final String VIDEO_S3_KEY_PREFIX = (System.getProperty("video.s3.key.prefix", STREAM_NAME + "/video"));

	private VideoWriter videoWriter;
	private BigInteger currentFragmentNumber = BigInteger.ZERO;
	private long currentRoundedTimestamp = 0L;
	private long nextRoundedTimestamp = 0L;
	private String currentVideoFileName = "";
	private String currentVideoFilePath = "";
	private AmazonS3 s3client;

	public OpencvVideoWriterConsumer() {
		super();
		File directory = new File(VIDEO_DIRECATORY);
		if (!directory.exists()) {
			directory.mkdirs();
		}
		if (IF_SAVE_VIDEO_FILE_S3) {
			s3client = AmazonS3ClientBuilder.standard().build();
		}
	}

	@Override
	protected void process(BufferedImage imageFrame, BigInteger fragmentNumber, long producerSideTimestamp, long sererSideTimestamp) {
		// Initialize for the first fragment
		if (currentFragmentNumber.equals(BigInteger.ZERO)) {
			visitNewFragment(fragmentNumber);
			// You can use both of producer side timestamp and server side timestamp
			configureVideoForInitialFragment(sererSideTimestamp);
		}

		// Convert BufferedImage to Mat
		Mat source = Java2DFrameUtils.toMat(imageFrame);

		// Write a frame by VideoWriter
		videoWriter.write(source);

		// Visit a new fragment
		if (fragmentNumber != currentFragmentNumber) {
			visitNewFragment(fragmentNumber);
			// You can use both of producer side timestamp and server side timestamp
			if (sererSideTimestamp > nextRoundedTimestamp) {
				configureVideoForNewFragment(sererSideTimestamp);
			}
		}
	}

	private void visitNewFragment(BigInteger fragmentNumber) {
		currentFragmentNumber = fragmentNumber;
	}

	private void configureVideoForInitialFragment(long currentTimestamp) {
		setRoundedVideoTimestamp(currentTimestamp);
		createVideoWriter();
	}

	private void configureVideoForNewFragment(long currentTimestamp) {
		releaseVideoWriter();
		setRoundedVideoTimestamp(currentTimestamp);
		createVideoWriter();
	}

	private void setVideoFileName() {
		currentVideoFileName = VIDEO_FILENAME_PREFIX + LOCAL_DTF.format(Instant.ofEpochMilli(currentRoundedTimestamp)) + ".mp4";
		currentVideoFilePath = VIDEO_DIRECATORY + "/" + currentVideoFileName;
	}

	private void setRoundedVideoTimestamp(long currentTimestamp) {
		currentRoundedTimestamp = currentTimestamp - (currentTimestamp % VIDEO_FILE_INTERVAL_MILLIS);
		nextRoundedTimestamp = currentRoundedTimestamp + VIDEO_FILE_INTERVAL_MILLIS;
	}

	private void createVideoWriter() {
		setVideoFileName();
		videoWriter = new VideoWriter(currentVideoFilePath, VideoWriter.fourcc((byte) 'M', (byte) 'P', (byte) '4', (byte) 'V'), VIDEO_FPS,
				new Size(VIDEO_WIDTH, VIDEO_HEIGHT));
	}

	private void releaseVideoWriter() {
		videoWriter.release();
		LOG.info("Saved video file as " + currentVideoFilePath);
		if (IF_SAVE_VIDEO_FILE_S3) {
			File videoFile = new File(currentVideoFilePath);
			String videoFileS3Path = VIDEO_S3_KEY_PREFIX + "/" + S3_DTF.format(Instant.ofEpochMilli(currentRoundedTimestamp)) + ".mp4";
			s3client.putObject(new PutObjectRequest(S3_BUCKET_NAME, videoFileS3Path, videoFile));
			LOG.info("Saved " + currentVideoFilePath + " to S3 as " + videoFileS3Path);
			if (videoFile.exists()) {
				videoFile.delete();
				LOG.info("Deleted video file from " + currentVideoFilePath);
			}
		}
	}

	@Override
	protected void quit() {
		File videoFile = new File(currentVideoFilePath);
		if (videoFile.exists()) {
			videoFile.delete();
		}
	}

	public static void main(String[] args) {
		AbstractKinesisVideoConsumer consumer = new OpencvVideoWriterConsumer();
		consumer.getMediaLoop();
	}

}
