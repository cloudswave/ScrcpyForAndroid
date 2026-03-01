package org.client.scrcpy.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 网络请求工具类
 */
public class HttpUtils {
    private static final String TAG = "HttpUtils";
    private static final int CONNECTION_TIMEOUT = 10000;  // 10s
    private static final int READ_TIMEOUT = 10000;         // 10s

    /**
     * 发送 POST 请求
     *
     * @param urlString URL 地址
     * @param jsonBody  JSON 请求体
     * @return 响应内容，失败返回 null
     */
    public static String post(String urlString, String jsonBody) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            // 发送请求体
            byte[] postData = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(postData.length));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(postData);
                os.flush();
            }

            // 读取响应
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "POST Response Code: " + responseCode);

            // regardless of code, return body (could be null if error reading)
            String respBody = readResponse(connection);
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_CREATED) {
                Log.e(TAG, "POST request returned non-OK code: " + responseCode + ", body=" + respBody);
            }
            return respBody;
        } catch (Exception e) {
            Log.e(TAG, "POST request error", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 读取 HTTP 响应内容
     */
    private static String readResponse(HttpURLConnection connection) {
        StringBuilder response = new StringBuilder();
        BufferedReader reader = null;
        try {
            // error responses come through getErrorStream()
            if (connection.getResponseCode() >= 400) {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            }
            String line;
            while (reader != null && (line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading response", e);
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return response.toString();
    }
}
