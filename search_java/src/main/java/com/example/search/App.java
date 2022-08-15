package com.example.search;

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

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().build();

        // ======================================================================
        // === Search by ID
        // ======================================================================

        String jsonSearchID = new JSONObject()
                .put("field", "id")
                .put("searchTerm", "1456789")
                .toString();

        long signSearchID_Timestamp = System.currentTimeMillis() / 1000L;
        String signSearchID_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "search", "search", jsonSearchID,
        signSearchID_Timestamp);

        // POST Request
        HttpPost requestSearchID = new HttpPost(API_HOST + "/v5/search");
        requestSearchID.addHeader("content-type", "application/json");
        requestSearchID.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestSearchID.addHeader("x-admiralcloud-rts", "" + signSearchID_Timestamp);
        requestSearchID.addHeader("x-admiralcloud-hash", signSearchID_Hash);
        requestSearchID.setEntity(new StringEntity(jsonSearchID, "text/plain", "UTF-8"));
        HttpResponse responseSearchID = httpClient.execute(requestSearchID);

        JSONObject jsonResultsSearchID = new JSONObject(EntityUtils.toString(responseSearchID.getEntity()));
        JSONArray jsonHitsSearchID = jsonResultsSearchID.getJSONObject("hits").getJSONArray("hits");

        System.out.println("Searching by ID. Results:");
        for (int i=0; i<jsonHitsSearchID.length(); i++) {
            JSONObject hit = jsonHitsSearchID.getJSONObject(i).getJSONObject("_source");
            System.out.println("- " + hit.getNumber("id") + " " + hit.getString("container_name"));
        }

        // ======================================================================
        // === Search by Default
        // ======================================================================

        String jsonSearchDefault = new JSONObject()
                .put("searchTerm", "test")
                .toString();

        long signSearchDefault_Timestamp = System.currentTimeMillis() / 1000L;
        String signSearchDefault_Hash = acRequestSignature(AUTH_ACCESS_SECRET, "search", "search", jsonSearchDefault,
        signSearchDefault_Timestamp);

        // POST Request
        HttpPost requestSearchDefault = new HttpPost(API_HOST + "/v5/search");
        requestSearchDefault.addHeader("content-type", "application/json");
        requestSearchDefault.addHeader("x-admiralcloud-accesskey", AUTH_ACCESS_KEY);
        requestSearchDefault.addHeader("x-admiralcloud-rts", "" + signSearchDefault_Timestamp);
        requestSearchDefault.addHeader("x-admiralcloud-hash", signSearchDefault_Hash);
        requestSearchDefault.setEntity(new StringEntity(jsonSearchDefault, "text/plain", "UTF-8"));
        HttpResponse responseSearchDefault = httpClient.execute(requestSearchDefault);

        JSONObject jsonResultsSearchDefault = new JSONObject(EntityUtils.toString(responseSearchDefault.getEntity()));
        JSONArray jsonHitsSearchDefault = jsonResultsSearchDefault.getJSONObject("hits").getJSONArray("hits");

        System.out.println("\n\nSearching by default. Results:");
        for (int i=0; i<jsonHitsSearchDefault.length(); i++) {
            JSONObject hit = jsonHitsSearchDefault.getJSONObject(i).getJSONObject("_source");
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