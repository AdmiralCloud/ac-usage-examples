package com.example.mediacontainerdelete;

import java.util.Map;
import java.util.HashMap;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    static Number MEDIACONTAINER_ID = 1886228;

    static String AUTH_ACCESS_SECRET = "1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX";
    static String AUTH_ACCESS_KEY = "idstXXXXN2X6XXXXpqWBVX";

    static String API_HOST = "https://api.admiralcloud.com";

    static class HttpDeleteWithEntity extends HttpPost
    {
        public HttpDeleteWithEntity(String url){
            super(url);
        }
        @Override
        public String getMethod() {
            return "DELETE";
        }
    }

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().build();

        String jsonMcDelete = new JSONObject()
                .put("forceDelete", true)
                .put("id", MEDIACONTAINER_ID)
                .put("notes", "Good reason to delete the MC")
                .toString();

        long signMcDelete_Timestamp = System.currentTimeMillis() / 1000L;
        String signMcDelete_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "mediacontainer", "destroy", jsonMcDelete,
                signMcDelete_Timestamp);

        HttpDeleteWithEntity requestMcDelete = new HttpDeleteWithEntity(API_HOST + "/v5/mediacontainer/" + MEDIACONTAINER_ID);
        requestMcDelete.addHeader("content-type", "application/json");
        requestMcDelete.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestMcDelete.addHeader("x-admiralcloud-rts", "" + signMcDelete_Timestamp);
        requestMcDelete.addHeader("x-admiralcloud-hash", signMcDelete_Hash);
        requestMcDelete.setEntity(new StringEntity(jsonMcDelete, "text/plain", "UTF-8"));
        HttpResponse responseMcDelete = httpClient.execute(requestMcDelete);

        System.out.println(EntityUtils.toString(responseMcDelete.getEntity()));
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