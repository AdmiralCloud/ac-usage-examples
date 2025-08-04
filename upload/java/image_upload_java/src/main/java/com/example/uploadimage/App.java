package com.example.uploadimage;

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
    
    // ACSignature instance
    private static final ACSignature acSignature = new ACSignature();
    
    public static void main(String[] args) {
        CloseableHttpClient httpClient = null;
        
        try {
            // Create HTTP client with appropriate timeout values
            httpClient = HttpClientBuilder.create()
                .setConnectionTimeToLive(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
            
            // Step 1: Initialize upload
            JSONObject createUploadResponse = initializeUpload(httpClient);
            String jobId = createUploadResponse.getString("jobId");
            System.out.println("JOBID=" + jobId);
            
            // Step 2: Get upload credentials (directly or via polling)
            JSONObject uploadData = getUploadCredentials(httpClient, createUploadResponse, jobId);
            
            // Step 3: Upload file to AWS S3
            uploadFileToS3(uploadData);
            
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
    private static JSONObject initializeUpload(CloseableHttpClient httpClient) throws Exception {
        String jsonPostData = new JSONObject()
                .put("payload", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "image")
                                .put("fileSize", 600)
                                .put("fileName", "foo.jpg")
                                .put("originalFileExtension", "jpg")))
                .put("controlGroups", new JSONArray())
                .put("tags", new JSONArray())
                .toString();
        
        return executeApiRequest(
            httpClient, 
            "POST", 
            "/v5/s3/createUpload", 
            jsonPostData
        );
    }
    
    /**
     * Gets the upload credentials, either from the initial response or through polling
     */
    private static JSONObject getUploadCredentials(
            CloseableHttpClient httpClient, 
            JSONObject createUploadResponse, 
            String jobId) throws Exception {
        
        // Check if credentials are already available in the first response
        if (createUploadResponse.has("processed") && 
            createUploadResponse.getJSONArray("processed").length() > 0 && 
            createUploadResponse.getJSONArray("processed").getJSONObject(0).has("credentials")) {
            
            System.out.println("Upload credentials available in initial response");
            return createUploadResponse.getJSONArray("processed").getJSONObject(0);
        }
        
        // For larger uploads: Wait for AWS S3 credentials to be provisioned
        System.out.println("Waiting for upload credentials via polling...");
        
        // Poll every 500ms until jobResult is no longer { ok: true }
        JSONObject jobResult;
        int maxRetries = 60; // 30 seconds maximum wait time
        int retryCount = 0;
        
        while (true) {
            if (retryCount >= maxRetries) {
                throw new IOException("Timed out waiting for upload credentials");
            }
            
            String endpoint = "/v5/activity/jobResult/" + jobId;
            jobResult = executeApiRequest(
                httpClient, 
                "GET", 
                endpoint, 
                new JSONObject().put("jobId", jobId).toString()
            );
            
            if (!jobResult.has("ok")) {
                break;
            }
            
            Thread.sleep(500);
            retryCount++;
        }
        
        // Process the result and return the upload data
        if (jobResult.has("processed") && 
            jobResult.getJSONArray("processed").length() > 0) {
            
            return jobResult.getJSONArray("processed").getJSONObject(0);
        }
        
        throw new IOException("No upload credentials found in API response");
    }
    
    /**
     * Uploads the file to AWS S3
     */
    private static void uploadFileToS3(JSONObject uploadData) throws Exception {
        JSONObject credentials = uploadData.getJSONObject("credentials");
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