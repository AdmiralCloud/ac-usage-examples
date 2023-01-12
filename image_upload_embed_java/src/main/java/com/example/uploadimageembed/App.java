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
import java.io.FileOutputStream;

public class App {
    static String AUTH_ACCESS_SECRET = "1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX";
    static String AUTH_ACCESS_KEY = "idstXXXXN2X6XXXXpqWBVX";

    static String API_HOST = "https://api.admiralcloud.com";
    static String PATH_IMAGE = "./image_for_upload.jpg";
    static String PATH_IMAGE_144P = "./image_144p.jpg";

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
                .put("waitForCompletion", true)
                .put("flag", 2)
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

        JSONObject jsonCreateUpload = new JSONObject(EntityUtils.toString(responseCreateUpload.getEntity()));
        System.out.println("uploadId=" + jsonCreateUpload.getString("uploadId"));

        // ======================================================================
        // === Step 2: Upload to AWS S3
        // ======================================================================
        JSONObject jsonUploadData = jsonCreateUpload.getJSONArray("processed").getJSONObject(0);
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
        // === Step 3: Tell AdmiralCloud to process file
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
        // === Step 4: Wait until Embedlink is available with Thumbnails
        // ======================================================================
        Number mediaContainerId = jsonCreateUpload.getJSONArray("processed").getJSONObject(0).getNumber("id");
        String dataGetEmbedlinks = new JSONObject()
                .put("mediaContainerId", mediaContainerId)
                .put("playerConfigurationId", FORMAT_ID)
                .toString();

        String urlImage144p = "";

        while (true) {
            long sigCreateEmbedlink_Timestamp = System.currentTimeMillis() / 1000L;
            String sigCreateEmbedlink_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "embedlink", "find",
                    dataGetEmbedlinks,
                    sigCreateEmbedlink_Timestamp);

            HttpGet requestGetEmbedlinks = new HttpGet(
                API_HOST + "/v5/embedlink/" + mediaContainerId + "?playerConfigurationId=" + FORMAT_ID
            );
            requestGetEmbedlinks.addHeader("content-type", "application/json");
            requestGetEmbedlinks.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
            requestGetEmbedlinks.addHeader("x-admiralcloud-rts", "" + sigCreateEmbedlink_Timestamp);
            requestGetEmbedlinks.addHeader("x-admiralcloud-hash", sigCreateEmbedlink_Hash);
            HttpResponse responseGetEmbedlinks = httpClient.execute(requestGetEmbedlinks);
            String strresp = EntityUtils.toString(responseGetEmbedlinks.getEntity());
            System.out.println("resp="+strresp);
            JSONArray jsonEmbedlinks = new JSONArray(strresp);
            
            if (jsonEmbedlinks.length() == 0) {
                System.out.println("No embedlink yet...");
                Thread.sleep(400);
                continue;
            }

            JSONObject jsonEmbedlink0 = jsonEmbedlinks.getJSONObject(0);
            if (!jsonEmbedlink0.getBoolean("hasThumbnailVersions")) {
                System.out.println("Embedlink.hasThumbnailVersions = false ...");
                Thread.sleep(400);
                continue;
            }

            urlImage144p = "https://images.admiralcloud.com/v5/deliverEmbed/"+jsonEmbedlink0.getString("link")+"/image/144";
            System.out.println("Embedlink = " + urlImage144p);
            break;
        }

        System.out.println("Upload finished and Embedlink available: https://app.admiralcloud.com/container/"+mediaContainerId+"/publish/weblink");


        // ======================================================================
        // === Step 5: Download 144p version
        // ======================================================================
        HttpGet request = new HttpGet(urlImage144p);

        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            FileOutputStream outstream = new FileOutputStream(new File(PATH_IMAGE_144P));
            entity.writeTo(outstream);
        }
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