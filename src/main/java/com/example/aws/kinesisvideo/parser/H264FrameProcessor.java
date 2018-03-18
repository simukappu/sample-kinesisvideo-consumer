package com.example.aws.kinesisvideo.parser;

import static org.jcodec.codecs.h264.H264Utils.splitMOVPacket;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.Transform;
import org.jcodec.scale.Yuv420jToRgb;

import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.MkvTrackMetadata;

public class H264FrameProcessor implements FrameVisitor.FrameProcessor {

	protected static final Log LOG = LogFactory.getLog(H264FrameProcessor.class);

	private int frameCount;
	private BufferedImage imageFrame;
	private byte[] codecPrivateData;
	private final H264Decoder decoder = new H264Decoder();
	private final Transform transform = new Yuv420jToRgb();

	private H264FrameProcessor() {
		super();
	}

	public static H264FrameProcessor create() {
		return new H264FrameProcessor();
	}

	public int getFrameCount() {
		return frameCount;
	}

	public BufferedImage getImageFrame() {
		return imageFrame;
	}

	public BufferedImage getCopiedImageFrame() {
		return copyImage(imageFrame);
	}

	public ByteBuffer getCodecPrivateData() {
		return ByteBuffer.wrap(codecPrivateData);
	}

	@Override
	public void process(Frame frame, MkvTrackMetadata trackMetadata) {
		ByteBuffer frameBuffer = frame.getFrameData();
		int pixelWidth = trackMetadata.getPixelWidth().get().intValue();
		int pixelHeight = trackMetadata.getPixelHeight().get().intValue();
		codecPrivateData = trackMetadata.getCodecPrivateData().array();
		LOG.debug("Decoding frames ... ");
		// Read the bytes that appear to comprise the header
		// See: https://www.matroska.org/technical/specs/index.html#simpleblock_structure

		Picture rgb = Picture.create(pixelWidth, pixelHeight, ColorSpace.RGB);
		imageFrame = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_3BYTE_BGR);
		AvcCBox avcC = AvcCBox.parseAvcCBox(ByteBuffer.wrap(codecPrivateData));

		decoder.addSps(avcC.getSpsList());
		decoder.addPps(avcC.getPpsList());

		Picture buf = Picture.create(pixelWidth, pixelHeight, ColorSpace.YUV420J);
		List<ByteBuffer> byteBuffers = splitMOVPacket(frameBuffer, avcC);
		Picture pic = decoder.decodeFrameFromNals(byteBuffers, buf.getData());

		if (pic != null) {
			// Work around for color issues in JCodec
			// https://github.com/jcodec/jcodec/issues/59
			// https://github.com/jcodec/jcodec/issues/192
			byte[][] dataTemp = new byte[3][pic.getData().length];
			dataTemp[0] = pic.getPlaneData(0);
			dataTemp[1] = pic.getPlaneData(2);
			dataTemp[2] = pic.getPlaneData(1);

			Picture tmpBuf = Picture.createPicture(pixelWidth, pixelHeight, dataTemp, ColorSpace.YUV420J);
			transform.transform(tmpBuf, rgb);
			AWTUtil.toBufferedImage(rgb, imageFrame);
			frameCount++;
		}
	}

	private BufferedImage copyImage(BufferedImage src) {
		BufferedImage dist = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
		dist.setData(src.getData());
		return dist;
	}
}
