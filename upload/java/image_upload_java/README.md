# AdmiralCloud Image Upload Example in Java

## Key Improvements

- Uses the official [ac-signature-java](https://github.com/AdmiralCloud/ac-signature-java) package for signature creation
- Optimized code with centralized API communication
- Improved error handling and resource management
- Updated Maven dependencies to latest versions
- Support for direct credential return for small uploads
- Better upload progress monitoring

## Prerequisites

- Java 11 or higher
- Maven

## Configuration

Before running the example, configure the following parameters in `App.java`:

```java
static String AUTH_ACCESS_SECRET = "your-access-secret";
static String AUTH_ACCESS_KEY = "your-access-key";
static String CLIENT_ID = "your-client-id";
static String PATH_IMAGE = "./path/to/your/image.jpg";
```

## Running the Example

```bash
# Build the project
mvn clean package

# Run the example
mvn exec:java -Dexec.mainClass="com.example.uploadimage.App"

# Alternative: Run with the JAR (including all dependencies)
java -jar target/uploadimage-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Upload Process Flow

1. **Initialize Upload**: The code sends a request to `/v5/s3/createUpload` to initiate the upload process.

2. **Get Upload Credentials**: 
   - For small uploads, the response from step 1 already contains the AWS credentials
   - For larger uploads, `/v5/activity/jobResult` is called to get the AWS credentials

3. **Upload File to AWS S3**: The file is uploaded to S3 using the AWS TransferManager with the correct region

4. **Notify AdmiralCloud**: After successful upload, a request is sent to `/v5/s3/success` to inform AdmiralCloud about the completed upload

## Troubleshooting

### Common Issues

- **Access Problems**: Ensure credentials are correctly configured
- **File Doesn't Exist**: Check the path to the image file
- **Network Issues**: Check your internet connection and firewall settings

### Logging

The program uses SLF4J with Simple Logger for debugging output. For more detailed logs, add this line to execution:

```bash
mvn exec:java -Dexec.mainClass="com.example.uploadimage.App" -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```