package org.client.scrcpy.utils;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.client.scrcpy.Constant;
import org.json.JSONObject;

/**
 * 授权服务类
 * 负责处理授权验证、保存和检查
 */
public class AuthorizationService {
    private static final String TAG = "AuthorizationService";

    /**
     * 获取设备码
     * 使用 Android ID 作为设备唯一标识
     */
    public static String getDeviceCode(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                return androidId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting device code", e);
        }
        // 备用：返回一个默认值
        return "UNKNOWN_DEVICE";
    }

    /**
     * 验证授权码
     * 通过 API 调用验证授权码
     *
     * @param context       Context
     * @param licenseKey    授权码
     * @param callback      回调函数
     */
    public static void verifyLicense(Context context, String licenseKey, AuthorizationCallback callback) {
        // 在后台线程中执行网络请求
        new Thread(() -> {
            try {
                String machineCode = "app_" + getDeviceCode(context);
                Log.d(TAG, "Device Code: " + machineCode);

                // 构造 JSON 请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("machine_code", machineCode);
                requestBody.put("license_key", licenseKey);

                String jsonBody = requestBody.toString();
                Log.d(TAG, "Request Body: " + jsonBody);

                // 发送 POST 请求
                String response = HttpUtils.post(Constant.LICENSE_API_URL, jsonBody);
                Log.d(TAG, "API Response: " + response);

                if (response != null) {
                    // 解析响应
                    JSONObject responseJson = new JSONObject(response);
                    String status = responseJson.optString("status");
                    String message = responseJson.optString("message", "");

                    Log.d(TAG, "Status: " + status + ", Message: " + message);

                    if ("success".equals(status)) {
                        // 授权成功，保存授权信息
                        saveLicenseInfo(context, licenseKey, machineCode, response);
                        if (callback != null) {
                            callback.onSuccess(message);
                        }
                    } else {
                        // 授权失败
                        if (callback != null) {
                            callback.onFailure(message.isEmpty() ? "Authorization failed" : message);
                        }
                    }
                } else {
                    // 网络请求失败
                    if (callback != null) {
                        callback.onFailure("Network request failed");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error verifying license", e);
                if (callback != null) {
                    callback.onFailure("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 保存授权信息
     */
    private static void saveLicenseInfo(Context context, String licenseKey, String machineCode, String response) {
        try {
            PreUtils.put(context, Constant.AUTHORIZATION_KEY, licenseKey);
            PreUtils.put(context, "MACHINE_CODE", machineCode);
            PreUtils.put(context, "LICENSE_RESPONSE", response);
            PreUtils.put(context, Constant.IS_AUTHORIZED, true);
            Log.d(TAG, "License info saved successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error saving license info", e);
        }
    }

    /**
     * 检查是否已授权
     */
    public static boolean isAuthorized(Context context) {
        return PreUtils.get(context, Constant.IS_AUTHORIZED, false);
    }

    /**
     * 获取保存的授权码
     */
    public static String getSavedLicenseKey(Context context) {
        return PreUtils.get(context, Constant.AUTHORIZATION_KEY, "");
    }

    /**
     * 清除授权信息
     */
    public static void clearLicenseInfo(Context context) {
        PreUtils.put(context, Constant.IS_AUTHORIZED, false);
        PreUtils.put(context, Constant.AUTHORIZATION_KEY, "");
        PreUtils.put(context, "MACHINE_CODE", "");
        PreUtils.put(context, "LICENSE_RESPONSE", "");
        Log.d(TAG, "License info cleared");
    }

    /**
     * 授权回调接口
     */
    public interface AuthorizationCallback {
        /**
         * 授权成功
         *
         * @param message 成功信息
         */
        void onSuccess(String message);

        /**
         * 授权失败
         *
         * @param error 错误信息
         */
        void onFailure(String error);
    }
}
