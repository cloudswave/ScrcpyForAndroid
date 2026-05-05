package org.client.scrcpy.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final String PREFS_NAME = "cloudphone_prefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_COOKIE = "cookie";
    private static final String KEY_USERNAME = "username";
    
    private static ApiClient instance;
    private String serverUrl = "";
    private String cookie = "";
    private String username = "";
    
    private SharedPreferences prefs;
    
    private ApiClient(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        serverUrl = prefs.getString(KEY_SERVER_URL, "");
        cookie = prefs.getString(KEY_COOKIE, "");
        username = prefs.getString(KEY_USERNAME, "");
    }
    
    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }
    
    public void setServerUrl(String url) {
        this.serverUrl = url;
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public boolean hasServerUrl() {
        return serverUrl != null && !serverUrl.isEmpty();
    }
    
    public void setCookie(String cookie) {
        this.cookie = cookie;
        prefs.edit().putString(KEY_COOKIE, cookie).apply();
    }
    
    public String getCookie() {
        return cookie;
    }
    
    public void setUsername(String username) {
        this.username = username;
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }
    
    public String getUsername() {
        return username;
    }
    
    public boolean isLoggedIn() {
        return cookie != null && !cookie.isEmpty();
    }
    
    public void logout() {
        cookie = "";
        username = "";
        prefs.edit()
            .putString(KEY_COOKIE, "")
            .putString(KEY_USERNAME, "")
            .apply();
    }
    
    /**
     * 登录
     * POST /api/auth/login
     * Body: {"username": "xxx", "password": "xxx"}
     */
    public LoginResult login(String username, String password) throws Exception {
        if (!hasServerUrl()) {
            throw new Exception("请先设置服务器地址");
        }
        
        String urlStr = serverUrl + "/api/auth/login";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", password);
        
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        
        int responseCode = conn.getResponseCode();
        Log.d(TAG, "Login response code: " + responseCode);
        
        if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Login response: " + response.toString());
            
            JSONObject json = new JSONObject(response.toString());
            if (json.optBoolean("success", false)) {
                // 获取 cookie
                List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
                if (cookies != null && !cookies.isEmpty()) {
                    String setCookie = cookies.get(0);
                    // 提取 connect.sid=xxx; 部分
                    if (setCookie.contains("connect.sid")) {
                        String sid = setCookie.split(";")[0];
                        setCookie(sid);
                    }
                }
                
                setUsername(username);
                
                JSONObject user = json.optJSONObject("user");
                String displayName = username;
                if (user != null) {
                    displayName = user.optString("username", username);
                }
                
                return new LoginResult(true, displayName, json.optBoolean("requirePasswordChange", false));
            } else {
                String message = json.optString("message", "登录失败");
                throw new Exception(message);
            }
        } else {
            throw new Exception("登录失败，HTTP " + responseCode);
        }
    }
    
    /**
     * 获取设备列表
     * GET /api/devices
     */
    public List<Device> getDevices() throws Exception {
        if (!isLoggedIn()) {
            throw new Exception("请先登录");
        }
        
        String urlStr = serverUrl + "/api/devices";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", cookie);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        int responseCode = conn.getResponseCode();
        Log.d(TAG, "Get devices response code: " + responseCode);
        
        if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Devices response: " + response.toString());
            
            List<Device> devices = new ArrayList<>();
            JSONArray jsonArray = new JSONArray(response.toString());
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                Device device = new Device();
                device.id = json.getInt("id");
                device.deviceSerial = json.getString("device_serial");
                device.deviceName = json.getString("device_name");
                device.createdAt = json.optString("created_at", "");
                
                // 直接从设备对象获取过期时间
                String expiresAt = json.optString("expires_at", "");
                if (expiresAt != null && !expiresAt.isEmpty() && !expiresAt.equals("null")) {
                    device.allocated = true;
                    device.expiresAt = expiresAt;
                }
                
                devices.add(device);
            }
            
            return devices;
        } else if (responseCode == 401) {
            logout();
            throw new Exception("登录已过期，请重新登录");
        } else {
            throw new Exception("获取设备列表失败，HTTP " + responseCode);
        }
    }
    
    /**
     * 兑换设备
     * POST /api/devices/activate
     * Body: {"code": "xxx"}
     */
    public Device activateDevice(String code) throws Exception {
        if (!isLoggedIn()) {
            throw new Exception("请先登录");
        }
        
        String urlStr = serverUrl + "/api/devices/activate";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Cookie", cookie);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        
        JSONObject body = new JSONObject();
        body.put("code", code);
        
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        
        int responseCode = conn.getResponseCode();
        Log.d(TAG, "Activate device response code: " + responseCode);
        
        if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Activate response: " + response.toString());
            
            JSONObject json = new JSONObject(response.toString());
            if (json.optBoolean("success", false)) {
                JSONObject deviceJson = json.optJSONObject("device");
                if (deviceJson != null) {
                    Device device = new Device();
                    device.id = deviceJson.getInt("id");
                    device.deviceSerial = deviceJson.getString("device_serial");
                    device.deviceName = deviceJson.getString("device_name");
                    device.createdAt = deviceJson.optString("created_at", "");
                    device.allocated = true;
                    return device;
                }
                return null;
            } else {
                String message = json.optString("message", "兑换失败");
                throw new Exception(message);
            }
        } else if (responseCode == 401) {
            logout();
            throw new Exception("登录已过期，请重新登录");
        } else {
            throw new Exception("兑换设备失败，HTTP " + responseCode);
        }
    }
    
    /**
     * 验证服务器连接
     */
    public boolean testConnection() {
        if (!hasServerUrl()) {
            return false;
        }
        try {
            String urlStr = serverUrl + "/api/auth/me";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (cookie != null && !cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            // 200=已登录，401=未登录但服务器可达
            return code == 200 || code == 401;
        } catch (Exception e) {
            Log.e(TAG, "Test connection failed", e);
            return false;
        }
    }
    
    public static class LoginResult {
        public boolean success;
        public String username;
        public boolean requirePasswordChange;
        
        public LoginResult(boolean success, String username, boolean requirePasswordChange) {
            this.success = success;
            this.username = username;
            this.requirePasswordChange = requirePasswordChange;
        }
    }
    
    public static class Device {
        public int id;
        public String deviceSerial;
        public String deviceName;
        public String createdAt;
        public boolean allocated;
        public String expiresAt;
        
        /**
         * 计算剩余天数
         */
        public int getDaysRemaining() {
            if (expiresAt == null || expiresAt.isEmpty() || expiresAt.equals("null")) {
                return -1; // -1 表示永不过期
            }
            try {
                // 处理 ISO 8601 格式: 2026-06-04T01:43:33.860Z
                String parsedDate = expiresAt.replace("T", " ").replace("Z", "");
                // 去除毫秒
                if (parsedDate.contains(".")) {
                    parsedDate = parsedDate.substring(0, parsedDate.indexOf("."));
                }
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                java.util.Date expiresDate = sdf.parse(parsedDate);
                if (expiresDate == null) return -1;
                
                long diff = expiresDate.getTime() - System.currentTimeMillis();
                if (diff <= 0) return 0;
                
                return (int) (diff / (1000 * 60 * 60 * 24));
            } catch (Exception e) {
                return -1;
            }
        }
    }
}