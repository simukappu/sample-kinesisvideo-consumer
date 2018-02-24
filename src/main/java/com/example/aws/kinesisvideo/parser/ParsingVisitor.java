package com.example.aws.kinesisvideo.parser;

import static org.jcodec.codecs.h264.H264Utils.splitMOVPacket;

import java.awt.image.BufferedImage;
import java.math.BigInteger;
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

import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvStartMasterElement;

public class ParsingVisitor extends MkvElementVisitor {

	protected static final Log LOG = LogFactory.getLog(ParsingVisitor.class);

	private BufferedImage imageFrame;
	private byte[] codecPrivate;
	private String codecID;
	private int pixelWidth = 0;
	private int pixelHeight = 0;
	private final H264Decoder decoder = new H264Decoder();
	private final Transform transform = new Yuv420jToRgb();

	public BufferedImage getImageFrame() {
		return imageFrame;
	}

	public BufferedImage getCopiedImageFrame() {
		return copyImage(imageFrame);
	}

	@Override
	public void visit(MkvStartMasterElement startMasterElement) throws MkvElementVisitException {
	}

	@Override
	public void visit(MkvEndMasterElement endMasterElement) throws MkvElementVisitException {
	}

	@Override
	public void visit(MkvDataElement dataElement) throws MkvElementVisitException {
		LOG.trace("Got data element: " + dataElement.getElementMetaData().getTypeInfo().getName());
		String dataElementName = dataElement.getElementMetaData().getTypeInfo().getName();

		if ("CodecID".equals(dataElementName)) {
			codecID = (String) dataElement.getValueCopy().getVal();
			// This is a C-style string, so remove the trailing null characters
			codecID = codecID.trim();
			LOG.trace("Codec ID: " + codecID);
		}

		if ("CodecPrivate".equals(dataElementName)) {
			codecPrivate = ((ByteBuffer) dataElement.getValueCopy().getVal()).array().clone();
			LOG.trace("CodecPrivate: " + codecPrivate);
		}

		if ("PixelWidth".equals(dataElementName)) {
			pixelWidth = ((BigInteger) dataElement.getValueCopy().getVal()).intValue();
			LOG.trace("Pixel Width: " + pixelWidth);
		}

		if ("PixelHeight".equals(dataElementName)) {
			pixelHeight = ((BigInteger) dataElement.getValueCopy().getVal()).intValue();
			LOG.trace("Pixel Height: " + pixelHeight);
		}

		if ("SimpleBlock".equals(dataElementName)) {
			LOG.trace("Decoding Frames ... ");
			ByteBuffer dataBuffer = dataElement.getDataBuffer();

			// Read the bytes that appear to comprise the header
			// See: https://www.matroska.org/technical/specs/index.html#simpleblock_structure

			byte[] header = new byte[4];
			dataBuffer.get(header, 0, 4);

			imageFrame = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_3BYTE_BGR);
			AvcCBox avcC = AvcCBox.parseAvcCBox(ByteBuffer.wrap(codecPrivate));

			decoder.addSps(avcC.getSpsList());
			decoder.addPps(avcC.getPpsList());

			Picture buf = Picture.create(pixelWidth, pixelHeight, ColorSpace.YUV420J);
			List<ByteBuffer> byteBuffers = splitMOVPacket(dataBuffer, avcC);
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
				Picture rgb = Picture.create(pixelWidth, pixelHeight, ColorSpace.RGB);
				transform.transform(tmpBuf, rgb);
				AWTUtil.toBufferedImage(rgb, imageFrame);
			}

		}
	}

	private BufferedImage copyImage(BufferedImage src) {
		BufferedImage dist = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
		dist.setData(src.getData());
		return dist;
	}
}
