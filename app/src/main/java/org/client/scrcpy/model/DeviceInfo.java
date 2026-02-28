package org.client.scrcpy.model;

public class DeviceInfo {
    private String name;
    private String ip;
    private String lastScreenshotPath;
    private boolean isConnected;

    public DeviceInfo(String name, String ip) {
        this.name = name;
        this.ip = ip;
        this.lastScreenshotPath = null;
        this.isConnected = false;
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