package com.example.uploadimageembed;

import java.util.Map;
import java.util.HashMap;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import java.io.File;

public class App {
    static String AUTH_ACCESS_SECRET = "1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX";
    static String AUTH_ACCESS_KEY = "idstXXXXN2X6XXXXpqWBVX";

    static String API_HOST = "https://api.admiralcloud.com";
    static String PATH_IMAGE = "./image_for_upload.jpg";

    static Number FORMAT_ID = 3;

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().build();

        // ======================================================================
        // === Step 1: Initialize Upload
        // ======================================================================

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

        long signatureTimestamp = System.currentTimeMillis() / 1000L;
        String signatureHash = acRequestSignature(AUTH_ACCESS_SECRET, "s3", "createUpload", jsonPostData,
                signatureTimestamp);

        // POST Request
        HttpPost requestCreateUpload = new HttpPost(API_HOST + "/v5/s3/createUpload");
        requestCreateUpload.addHeader("content-type", "application/json");
        requestCreateUpload.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestCreateUpload.addHeader("x-admiralcloud-rts", "" + signatureTimestamp);
        requestCreateUpload.addHeader("x-admiralcloud-hash", signatureHash);
        requestCreateUpload.setEntity(new StringEntity(jsonPostData, "text/plain", "UTF-8"));
        HttpResponse responseCreateUpload = httpClient.execute(requestCreateUpload);

        String jobId = new JSONObject(EntityUtils.toString(responseCreateUpload.getEntity())).getString("jobId");
        System.out.println("JOBID=" + jobId);

        // ======================================================================
        // === Step 2: Wait for AWS S3 storage to be initialized
        // ======================================================================
        JSONObject jsonJobResult;
        // Poll every 500ms until jobResult is no longer { ok: true }
        while (true) {
            long sigJobResult_Timestamp = System.currentTimeMillis() / 1000L;
            String sigJobResult_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "activity", "jobResult",
                    new JSONObject().put("jobId", jobId).toString(),
                    sigJobResult_Timestamp);

            HttpGet requestJobResult = new HttpGet(API_HOST + "/v5/activity/jobResult/" + jobId);
            requestJobResult.addHeader("content-type", "application/json");
            requestJobResult.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
            requestJobResult.addHeader("x-admiralcloud-rts", "" + sigJobResult_Timestamp);
            requestJobResult.addHeader("x-admiralcloud-hash", sigJobResult_Hash);
            HttpResponse responseJobResult = httpClient.execute(requestJobResult);
            jsonJobResult = new JSONObject(EntityUtils.toString(responseJobResult.getEntity()));

            if (!jsonJobResult.has("ok")) {
                break;
            }
            Thread.sleep(500);
        }

        System.out.println(jsonJobResult.toString(4));

        // ======================================================================
        // === Step 3: Upload to AWS S3
        // ======================================================================
        JSONObject jsonUploadData = jsonJobResult.getJSONArray("processed").getJSONObject(0);
        JSONObject jsonUploadCredentials = jsonUploadData.getJSONObject("credentials");

        File fileImage = new File(PATH_IMAGE);
        TransferManager xfer_mgr = TransferManagerBuilder
                .standard().withS3Client(AmazonS3ClientBuilder.standard().withCredentials(
                        new AWSStaticCredentialsProvider(new BasicSessionCredentials(
                                jsonUploadCredentials.getString("AccessKeyId"),
                                jsonUploadCredentials.getString("SecretAccessKey"),
                                jsonUploadCredentials.getString("SessionToken"))))
                        .build())
                .build();
        try {
            Upload xfer = xfer_mgr.upload(jsonUploadData.getString("bucket"), jsonUploadData.getString("s3Key"),
                    fileImage);
            do {
                Thread.sleep(500);
                System.out.println("PROGRESS: " + xfer.getProgress().getPercentTransferred() + " %");
            } while (!xfer.isDone());
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }
        xfer_mgr.shutdownNow();

        // ======================================================================
        // === Step 4: Tell AdmiralCloud to process file
        // ======================================================================
        String dataS3Success = new JSONObject().put("bucket", jsonUploadData.getString("bucket"))
                .put("key", jsonUploadData.getString("s3Key")).toString();
        long sigS3Success_Timestamp = System.currentTimeMillis() / 1000L;
        String sigS3Success_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "s3", "success",
                dataS3Success,
                sigS3Success_Timestamp);

        HttpPost requestS3Success = new HttpPost(API_HOST + "/v5/s3/success");
        requestS3Success.addHeader("content-type", "application/json");
        requestS3Success.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestS3Success.addHeader("x-admiralcloud-rts", "" + sigS3Success_Timestamp);
        requestS3Success.addHeader("x-admiralcloud-hash", sigS3Success_Hash);
        requestS3Success.setEntity(new StringEntity(dataS3Success, "text/plain", "UTF-8"));
        HttpResponse responseS3Success = httpClient.execute(requestS3Success);

        // ======================================================================
        // === Step 5: Get Embedlink
        // ======================================================================
        Number mediaContainerId = jsonJobResult.getJSONArray("processed").getJSONObject(0).getNumber("id");
        String dataCreateEmbedlink = new JSONObject()
                .put("mediaContainerId", mediaContainerId)
                .put("playerConfigurationId", FORMAT_ID)
                .toString();
        long sigCreateEmbedlink_Timestamp = System.currentTimeMillis() / 1000L;
        String sigCreateEmbedlink_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "embedlink", "create",
                dataCreateEmbedlink,
                sigCreateEmbedlink_Timestamp);

        HttpPost requestCreateEmbedlink = new HttpPost(API_HOST + "/v5/embedlink/" + mediaContainerId);
        requestCreateEmbedlink.addHeader("content-type", "application/json");
        requestCreateEmbedlink.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestCreateEmbedlink.addHeader("x-admiralcloud-rts", "" + sigCreateEmbedlink_Timestamp);
        requestCreateEmbedlink.addHeader("x-admiralcloud-hash", sigCreateEmbedlink_Hash);
        requestCreateEmbedlink.setEntity(new StringEntity(dataCreateEmbedlink, "text/plain", "UTF-8"));
        HttpResponse responseCreateEmbedlink = httpClient.execute(requestCreateEmbedlink);
        JSONObject jsonEmbedlink = new JSONObject(EntityUtils.toString(responseCreateEmbedlink.getEntity()));
        System.out.println("Embedlink = https://images.admiralcloud.com/v5/deliverEmbed/"+jsonEmbedlink.getString("link")+"/image");

        System.out.println("Upload finished: https://app.admiralcloud.com/container/"+mediaContainerId+"/publish/weblink");
    }

    static String acRequestSignature(String secretKey, String controller, String action, String jsonData,
            long timeStamp) throws Exception {
        // Prepare HMAC
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secretKeySpec);

        // JSON objects must be ordered by key to generate consistent JSON Strings
        ObjectMapper om = new ObjectMapper();
        om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        Map<String, Object> map = om.readValue(jsonData, HashMap.class);
        String jsonData_ordered = om.writeValueAsString(map);

        // generate signature string and encode it
        String data = controller.toLowerCase() + "\n" + action.toLowerCase() + "\n" + timeStamp + "\n"
                + jsonData_ordered;
        return Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
    }
}