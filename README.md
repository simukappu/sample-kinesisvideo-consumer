# sample-kinesisvideo-consumer
Sample applications as Amazon Kinesis Video Streams consumers

# Preparation for the consumer
See https://github.com/awslabs/amazon-kinesis-video-streams-producer-sdk-cpp.

You can use GStreamer Demo App in this producer SDK.

# Preparation for the consumer
1. Clone this repository
2. Import this application to IDE like Eclipse
3. Clone the following *amazon-kinesis-video-streams-parser-library* repository and add to build path
   https://github.com/aws/amazon-kinesis-video-streams-parser-library
4. Create your stream of Amazon Kinesis Video Streams and set region and stream name as JVM arguments
5. Set your AWS credentials as default to access your stream of Amazon Kinesis Video Streams
6. Run the producer
7. Run the consumer in *src/main/java/com/example/aws/kinesisvideo/consumer* directory
