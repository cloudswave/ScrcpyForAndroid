package org.client.scrcpy.navigation;

import android.content.Context;
import android.content.Intent;

import org.client.scrcpy.MainActivity;
import org.client.scrcpy.DeviceListActivity;

public class NavigationManager {
    
    private static NavigationManager instance;
    
    private NavigationManager() {
    }
    
    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }
    
    /**
     * 从MainActivity跳转到DeviceListActivity
     */
    public void navigateToDeviceList(Context context) {
        Intent intent = new Intent(context, DeviceListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
    
    /**
     * 从DeviceListActivity跳转到MainActivity
     */
    public void navigateToMain(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}