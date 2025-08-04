package com.example.uploadreplaceimage;

import java.util.Map;
import java.util.HashMap;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.regions.Regions;
import com.admiralcloud.signature.ACSignature;
import com.admiralcloud.signature.SignatureRequest;
import com.admiralcloud.signature.SignatureResponse;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import com.amazonaws.http.IdleConnectionReaper;

public class App {
    // Configuration parameters
    static String AUTH_ACCESS_SECRET = "SECRET-ACCESS-SECRET";
    static String AUTH_ACCESS_KEY = "ACCESS–KEY";
    static String CLIENT_ID = "your-client-id";
    static String API_HOST = "https://api.admiralcloud.com";
    static String PATH_IMAGE = "./image_for_upload.png";
    static String AC_SIGNATURE_VERSION = "5";
    static int MEDIA_CONTAINER_ID = 12341234;
    
    // ACSignature instance
    private static final ACSignature acSignature = new ACSignature();
    
    public static void main(String[] args) {
        CloseableHttpClient httpClient = null;
        
        try {
            // Create HTTP client with appropriate timeout values
            httpClient = HttpClientBuilder.create()
                .setConnectionTimeToLive(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
            
            // Step 1: Create a media item
            JSONObject uploadData = createMediaItem(httpClient);
            
            // Step 2: Get upload credentials
            JSONObject credentials = initializeUpload(httpClient, uploadData.getInt("id"));
            
            // Step 3: Upload file to AWS S3
            uploadFileToS3(uploadData, credentials);
            
            // Step 4: Notify AdmiralCloud that upload was successful
            notifyUploadSuccess(httpClient, uploadData);
            
            System.out.println("Upload completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error executing upload: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up HTTP client resources
            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    System.err.println("Error closing HTTP client: " + e.getMessage());
                }
            }
            // Shut down AWS reaper thread explicitly
            try {
                IdleConnectionReaper.shutdown();
            } catch (Exception e) {
                System.err.println("Failed to shut down IdleConnectionReaper: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a new media item in AdmiralCloud
     */
    private static JSONObject createMediaItem(CloseableHttpClient httpClient) throws Exception {
        File imageFile = new File(PATH_IMAGE);
        String fileName = imageFile.getName();

        JSONObject replacement = new JSONObject();
        replacement.put("id", MEDIA_CONTAINER_ID);
        replacement.put("skipVersion", true);

        JSONObject additionalSettings = new JSONObject();
        additionalSettings.put("originalFileExtension", "jpg");
        additionalSettings.put("replacement", replacement);

        JSONObject payload = new JSONObject();
        payload.put("mediaContainerId", MEDIA_CONTAINER_ID);
        payload.put("source", "user");
        payload.put("type", "image");
        payload.put("fileName", fileName);
        payload.put("formatId", 21);
        payload.put("additionalSettings", additionalSettings);

        JSONObject response = executeApiRequest(
            httpClient,
            "POST",
            "/v5/media",
            payload.toString()
        );
        return response;
    }
    
    /**
     * Executes an HTTP request against the AdmiralCloud API
     * 
     * @param httpClient The HTTP client to use
     * @param method The HTTP method ("GET" or "POST")
     * @param endpoint The complete API endpoint
     * @param jsonData The JSON data to send (or null for GET requests)
     * @return The API response as JSONObject
     */
    private static JSONObject executeApiRequest(
            CloseableHttpClient httpClient, 
            String method, 
            String endpoint, 
            String jsonData) throws Exception {
        
        // Create signature request
        SignatureRequest signatureRequest = new SignatureRequest();
        signatureRequest.setAccessSecret(AUTH_ACCESS_SECRET);
        signatureRequest.setPath(endpoint);
        if (jsonData != null && !jsonData.isEmpty()) {
            signatureRequest.setPayload(jsonData);
        }
        
        // Generate signature
        SignatureResponse signatureResponse = acSignature.sign(signatureRequest);
        
        CloseableHttpResponse response = null;
        
        try {
            if ("GET".equals(method)) {
                HttpGet request = new HttpGet(API_HOST + endpoint);
                request.addHeader("content-type", "application/json");
                request.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
                request.addHeader("x-admiralcloud-rts", String.valueOf(signatureResponse.getTimestamp()));
                request.addHeader("x-admiralcloud-hash", signatureResponse.getHash());
                request.addHeader("x-admiralcloud-clientid", CLIENT_ID);
                request.addHeader("x-admiralcloud-version", AC_SIGNATURE_VERSION);
                
                response = httpClient.execute(request);
            } 
            else if ("POST".equals(method)) {
                HttpPost request = new HttpPost(API_HOST + endpoint);
                request.addHeader("content-type", "application/json");
                request.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
                request.addHeader("x-admiralcloud-rts", String.valueOf(signatureResponse.getTimestamp()));
                request.addHeader("x-admiralcloud-hash", signatureResponse.getHash());
                request.addHeader("x-admiralcloud-clientid", CLIENT_ID);
                request.addHeader("x-admiralcloud-version", AC_SIGNATURE_VERSION);
                
                if (jsonData != null) {
                    request.setEntity(new StringEntity(jsonData, "UTF-8"));
                }
                
                response = httpClient.execute(request);
            }
            else {
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
            
            // Check HTTP status code
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("API request failed with status " + statusCode + 
                        ": " + response.getStatusLine().getReasonPhrase());
            }
            
            // Process response
            String responseBody = EntityUtils.toString(response.getEntity());
            return new JSONObject(responseBody);
            
        } catch (SocketTimeoutException e) {
            throw new IOException("API request timed out: " + e.getMessage(), e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    System.err.println("Error closing HTTP response: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Initializes the upload process
     */
    private static JSONObject initializeUpload(CloseableHttpClient httpClient, int mediaId) throws Exception {
        return executeApiRequest(
            httpClient, 
            "GET", 
            "/v5/s3/getUploadCredentials/" + mediaId, 
            new JSONObject().put("mediaId", mediaId).toString() 
        );
    }
    
    
    
    /**
     * Uploads the file to AWS S3
     */
    private static void uploadFileToS3(JSONObject uploadData, JSONObject credentials) throws Exception {
        String bucket = uploadData.getString("bucket");
        String s3Key = uploadData.getString("s3Key");
        
        File imageFile = new File(PATH_IMAGE);
        if (!imageFile.exists() || !imageFile.isFile()) {
            throw new IOException("File " + PATH_IMAGE + " does not exist or is not a file");
        }
        
        // Create S3 client and TransferManager
        AmazonS3 s3Client = null;
        TransferManager transferManager = null;
        
        try {
            // AWS credentials for the upload
            BasicSessionCredentials awsCredentials = new BasicSessionCredentials(
                credentials.getString("AccessKeyId"),
                credentials.getString("SecretAccessKey"),
                credentials.getString("SessionToken")
            );
            
            // Get the region from the API response
            String region = uploadData.getString("region");
            
            // Create S3 client with the specific region
            s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withRegion(region) // Use the region from the API response
                .build();
            
            // TransferManager for optimized upload
            transferManager = TransferManagerBuilder.standard()
                .withS3Client(s3Client)
                .build();
            
            System.out.println("Starting S3 upload...");
            System.out.println("Bucket: " + bucket);
            System.out.println("S3 Key: " + s3Key);
            
            // Start upload
            Upload upload = transferManager.upload(bucket, s3Key, imageFile);
            
            // Monitor upload progress
            while (!upload.isDone()) {
                try {
                    System.out.printf("Upload progress: %.2f%%\n", 
                        upload.getProgress().getPercentTransferred());
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Upload was interrupted", e);
                }
            }
            
            // Check upload status
            upload.waitForCompletion();
            System.out.println("S3 upload completed");
            
        } catch (AmazonServiceException e) {
            throw new IOException("AWS S3 service error during upload: " + e.getMessage(), e);
        } catch (AmazonClientException e) {
            throw new IOException("AWS S3 client error during upload: " + e.getMessage(), e);
        } finally {
            // Clean up resources
            if (transferManager != null) {
                transferManager.shutdownNow(false);
            }
        }
    }
    
    /**
     * Notifies AdmiralCloud about the successful upload
     */
    private static void notifyUploadSuccess(CloseableHttpClient httpClient, JSONObject uploadData) throws Exception {
        String jsonData = new JSONObject()
            .put("bucket", uploadData.getString("bucket"))
            .put("key", uploadData.getString("s3Key"))
            .toString();
        
        JSONObject response = executeApiRequest(
            httpClient, 
            "POST", 
            "/v5/s3/success", 
            jsonData
        );
    }
}