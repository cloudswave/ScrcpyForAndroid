package org.client.scrcpy;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.util.HashMap;
import java.util.Map;

public class ScrcpyClient {
    private static final String TAG = "ScrcpyClient";
    private static final Map<String, Long> lastScreenshotSizes = new HashMap<>();
    
    /**
     * Check if a device is reachable via ADB over network
     * @param ip The IP address of the device
     * @param port The port number
     * @return true if connection is successful, false otherwise
     */
    public static boolean isDeviceReachable(String ip, int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            // Set connection timeout to 5 seconds
            socket.connect(new InetSocketAddress(ip, port), 5000);
            Log.d(TAG, "Successfully connected to device: " + ip + ":" + port);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect to device: " + ip + ":" + port, e);
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket", e);
                }
            }
        }
    }
    
    /**
     * Get a screenshot from a device via ADB
     * @param ip The IP address of the device
     * @param port The port number
     * @return Path to the saved screenshot, or null if failed
     */
    public static String getScreenshotFromDevice(String ip, int port) {
        // Create a unique key for this device
        String deviceKey = ip + ":" + port;
        
        // Check if the device is reachable
        if (!isDeviceReachable(ip, port)) {
            Log.e(TAG, "Device not reachable: " + deviceKey);
            return null;
        }
        
        try {
            // Connect to the device first
            App.adbCmd("connect", deviceKey);
            
            // Execute the screenshot command and save to a temporary file on the device
            String tempScreenshotPath = "/data/local/tmp/screenshot.png";
            String result = App.adbCmd("-s", deviceKey, "shell", "screencap", "-p", tempScreenshotPath);
            
            // Get the size of the screenshot on the device
            String sizeResult = App.adbCmd("-s", deviceKey, "shell", "stat", "-c", "%s", tempScreenshotPath);
            long remoteSize = 0;
            try {
                remoteSize = Long.parseLong(sizeResult.trim());
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing remote file size: " + sizeResult);
                return null;
            }
            
            // Check if we have a previous size for this device
            Long lastSize = lastScreenshotSizes.get(deviceKey);
            String localPath = App.mContext.getCacheDir().getAbsolutePath() + "/" + ip.replace(".", "_") + "_" + port + "_" + lastSize + ".png";
            String newPath = App.mContext.getCacheDir().getAbsolutePath() + "/" + ip.replace(".", "_") + "_" + port + "_" + remoteSize + ".png";
            // If size is the same and local file exists, use it
            if (lastSize != null && lastSize == remoteSize) {
                java.io.File localFile = new java.io.File(localPath);
                if (localFile.exists()) {
                    Log.d(TAG, "Screenshot size unchanged, using previous file: " + localPath);
                    // Clean up the temporary file on the device
                    App.adbCmd("-s", deviceKey, "shell", "rm", tempScreenshotPath);
                    return localPath;
                }
            } else {
                // Clean up the previous screenshot file if it exists
                java.io.File prevFile = new java.io.File(localPath);
                if (prevFile.exists()) {
                    prevFile.delete();
                }
            }
            lastScreenshotSizes.put(deviceKey, remoteSize);
            localPath = newPath;
            // Pull the screenshot from the device to app cache directory
            result = App.adbCmd("-s", deviceKey, "pull", tempScreenshotPath, localPath);
            Log.d(TAG, "Pulling screenshot from device: " + result);
        
            // Check if the file exists and has content
            java.io.File screenshotFile = new java.io.File(localPath);
            if (!screenshotFile.exists()) {
                Log.e(TAG, "Screenshot file not found: " + localPath);
                return null;
            }
            
            // Compress the screenshot to reduce size and resolution
            String compressedPath = compressImage(localPath);
            if (compressedPath != null) {
                localPath = compressedPath;
            }
            
            // Get size after compression
            java.io.File finalFile = new java.io.File(localPath);
            long finalSize = finalFile.length();
            
            // Clean up the temporary file on the device
            App.adbCmd("-s", deviceKey, "shell", "rm", tempScreenshotPath);
            
            Log.d(TAG, "Screenshot saved to: " + localPath + ", size: " + finalSize + " bytes");
            return localPath;
        } catch (Exception e) {
            Log.e(TAG, "Error getting screenshot", e);
            return null;
        }
    }
    
    /**
     * Compress an image to reduce its size and resolution
     * @param imagePath Path to the original image
     * @return Path to the compressed image, or null if compression fails
     */
    private static String compressImage(String imagePath) {
        try {
            // Decode the image with inSampleSize to reduce memory usage
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(imagePath, options);
            
            // Calculate inSampleSize
            int maxSize = 1024; // Maximum width or height
            int inSampleSize = 1;
            if (options.outHeight > maxSize || options.outWidth > maxSize) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= maxSize && (halfWidth / inSampleSize) >= maxSize) {
                    inSampleSize *= 2;
                }
            }
            
            // Decode with inSampleSize
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(imagePath, options);
            
            // Compress the bitmap
            String compressedPath = imagePath.replace(".png", "_compressed.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(compressedPath);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, fos);
            fos.flush();
            fos.close();
            bitmap.recycle();
            
            // Delete the original file
            java.io.File originalFile = new java.io.File(imagePath);
            if (originalFile.exists()) {
                originalFile.delete();
            }
            
            // Rename compressed file to original name
            java.io.File compressedFile = new java.io.File(compressedPath);
            if (compressedFile.exists()) {
                compressedFile.renameTo(originalFile);
                return imagePath;
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error compressing image", e);
            return null;
        }
    }

}