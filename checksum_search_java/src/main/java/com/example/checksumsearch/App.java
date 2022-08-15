package com.example.checksumsearch;

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
import org.json.JSONArray;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {
    static String AUTH_ACCESS_SECRET = "1d53c176-XXXX-43ea-XXXX-1eXXXX4aXXXX";
    static String AUTH_ACCESS_KEY = "idstXXXXN2X6XXXXpqWBVX";

    static String API_HOST = "https://api.admiralcloud.com";
    static String CHECKSUM = "6fc5bbfc2ba81f72ab3ab05ed9ed9b8a";

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().build();

        // ======================================================================
        // === Search by CHECKSUM
        // ======================================================================

        String jsonSearchCHECKSUM = new JSONObject()
                .put("field", "checksum")
                .put("searchTerm", "v2:" + CHECKSUM)
                .toString();

        long signSearchCHECKSUM_Timestamp = System.currentTimeMillis() / 1000L;
        String signSearchCHECKSUM_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "search", "search", jsonSearchCHECKSUM,
        signSearchCHECKSUM_Timestamp);

        // POST Request
        HttpPost requestSearchCHECKSUM = new HttpPost(API_HOST + "/v5/search");
        requestSearchCHECKSUM.addHeader("content-type", "application/json");
        requestSearchCHECKSUM.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestSearchCHECKSUM.addHeader("x-admiralcloud-rts", "" + signSearchCHECKSUM_Timestamp);
        requestSearchCHECKSUM.addHeader("x-admiralcloud-hash", signSearchCHECKSUM_Hash);
        requestSearchCHECKSUM.setEntity(new StringEntity(jsonSearchCHECKSUM, "text/plain", "UTF-8"));
        HttpResponse responseSearchCHECKSUM = httpClient.execute(requestSearchCHECKSUM);

        JSONObject jsonResultsSearchCHECKSUM = new JSONObject(EntityUtils.toString(responseSearchCHECKSUM.getEntity()));
        JSONArray jsonHitsSearchCHECKSUM = jsonResultsSearchCHECKSUM.getJSONObject("hits").getJSONArray("hits");

        System.out.println("Searching by CHECKSUM. Results:");
        for (int i=0; i<jsonHitsSearchCHECKSUM.length(); i++) {
            JSONObject hit = jsonHitsSearchCHECKSUM.getJSONObject(i).getJSONObject("_source");
            System.out.println("- " + hit.getNumber("id") + " " + hit.getString("container_name"));
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