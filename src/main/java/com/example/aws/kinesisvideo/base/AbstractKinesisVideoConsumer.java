package com.example.aws.kinesisvideo.base;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClient;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.amazonaws.services.kinesisvideo.model.StartSelector;
import com.amazonaws.services.kinesisvideo.model.StartSelectorType;

public abstract class AbstractKinesisVideoConsumer {

	protected static final String REGION = System.getProperty("region", "ap-northeast-1");
	protected static final String STREAM_NAME = System.getProperty("stream.name", "tokyo-video-stream-1");
	private static final long GETMEDIA_INTERVAL = Integer.parseInt(System.getProperty("getmedia.interval", "3000"));
	protected static final Log LOG = LogFactory.getLog(AbstractKinesisVideoConsumer.class);

	private Region region;
	private String streamName;
	private String endpointUrl;
	private AmazonKinesisVideoMedia mediaClient;

	public AbstractKinesisVideoConsumer() {
		this(REGION, STREAM_NAME);
	}

	public AbstractKinesisVideoConsumer(String regionName, String streamName) {
		super();
		setRegion(regionName);
		setStreamName(streamName);
		prepareMediaClient();
	}

	protected abstract void consume(GetMediaResult result);

	protected abstract void quit();

	public Region getRegion() {
		return region;
	}

	public void setRegion(Region region) {
		this.region = region;
		prepareEndpointUrl(region.getName());
	}

	public void setRegion(String regionName) {
		this.region = Region.getRegion(Regions.fromName(regionName));
		prepareEndpointUrl(region.getName());
	}

	public String getStreamName() {
		return streamName;
	}

	public void setStreamName(String streamName) {
		this.streamName = streamName;
	}

	public String getEndpointUrl() {
		return endpointUrl;
	}

	protected void prepareEndpointUrl(String regionName) {
		this.endpointUrl = String.format("https://kinesisvideo.%s.amazonaws.com", regionName);
	}

	public void prepareMediaClient() {
		AmazonKinesisVideo client = AmazonKinesisVideoClientBuilder.standard().withCredentials(new ProfileCredentialsProvider())
				.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUrl, region.getName())).build();

		String getMediaEndpoint = client
				.getDataEndpoint(new GetDataEndpointRequest().withStreamName(streamName).withAPIName(APIName.GET_MEDIA)).getDataEndpoint();
		LOG.info("GetMedia endpoint: " + getMediaEndpoint);

		this.mediaClient = AmazonKinesisVideoMediaClient.builder().withCredentials(new ProfileCredentialsProvider())
				.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(getMediaEndpoint, region.getName())).build();
	}

	private GetMediaRequest getMediaRequest() {
		final StartSelector startSelector = new StartSelector().withStartSelectorType(StartSelectorType.NOW);
		final GetMediaRequest getMediaRequest = new GetMediaRequest().withStartSelector(startSelector).withStreamName(streamName);
		return getMediaRequest;
	}

	public void getMedia() {
		GetMediaResult result = mediaClient.getMedia(getMediaRequest());
		LOG.info("GetMedia request Id: " + result.getSdkResponseMetadata().getRequestId());
		LOG.info("GetMedia response started: " + result.getContentType());
		consume(result);
	}

	public void getMediaLoop() {
		while (true) {
			getMedia();
			quit();
			try {
				Thread.sleep(GETMEDIA_INTERVAL);
			} catch (InterruptedException e) {
				System.exit(0);
			}
			LOG.info("GetMedia response is not found from this stream. Trying again...");
		}
	}
}
