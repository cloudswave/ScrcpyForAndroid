package org.client.scrcpy.model;

public class DeviceInfo {
    private String name;
    private String ip;
    private String lastScreenshotPath;
    private boolean isConnected;
    private int daysRemaining;
    private String expiresAt;

    public DeviceInfo(String name, String ip) {
        this.name = name;
        this.ip = ip;
        this.lastScreenshotPath = null;
        this.isConnected = false;
        this.daysRemaining = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(int days) {
        this.daysRemaining = days;
    }
    
    public String getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getLastScreenshotPath() {
        return lastScreenshotPath;
    }

    public void setLastScreenshotPath(String lastScreenshotPath) {
        this.lastScreenshotPath = lastScreenshotPath;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }
}