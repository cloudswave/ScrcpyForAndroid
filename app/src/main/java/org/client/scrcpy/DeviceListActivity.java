package org.client.scrcpy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.client.scrcpy.adapter.DeviceAdapter;
import org.client.scrcpy.model.DeviceInfo;
import org.client.scrcpy.utils.PreUtils;
import org.client.scrcpy.ScrcpyClient;
import org.client.scrcpy.navigation.NavigationManager;

import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends Activity {

    private GridView deviceGrid;
    private DeviceAdapter deviceAdapter;
    private List<DeviceInfo> deviceList;
    private Handler handler;
    private Runnable screenshotRunnable;
    private ImageButton addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_list_activity);

        initViews();
        loadDevices();
        setupAdapter();
        setupClickListeners();
        setupBottomNavigation();
        
        // Check clipboard for device list
        checkClipboardForDevices();
        
        // Start the screenshot update loop
        startScreenshotUpdates();
    }
    
    private void checkClipboardForDevices() {
        // Get clipboard manager
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        
        // Check if clipboard has text
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            android.content.ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null) {
                    // Parse the clipboard text for IP:port format
                    String clipboardText = text.toString();
                    android.util.Log.d("DeviceListActivity", "Clipboard content: " + clipboardText);
                    
                    String[] lines = clipboardText.split("\\n");
                    android.util.Log.d("DeviceListActivity", "Number of lines: " + lines.length);
                    
                    // List to store valid devices
                    List<String> devicesFromClipboard = new ArrayList<>();
                    
                    // Regular expression for IP:port format
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+$");
                    
                    for (String line : lines) {
                        String trimmedLine = line.trim();
                        android.util.Log.d("DeviceListActivity", "Processing line: '" + trimmedLine + "'");
                        if (!trimmedLine.isEmpty()) {
                            java.util.regex.Matcher matcher = pattern.matcher(trimmedLine);
                            if (matcher.matches()) {
                                devicesFromClipboard.add(trimmedLine);
                                android.util.Log.d("DeviceListActivity", "Added valid device: " + trimmedLine);
                            } else {
                                android.util.Log.d("DeviceListActivity", "Invalid format: " + trimmedLine);
                            }
                        }
                    }
                    
                    android.util.Log.d("DeviceListActivity", "Found " + devicesFromClipboard.size() + " valid devices");
                    // If we found multiple devices, show import dialog
                    if (devicesFromClipboard.size() > 1) {
                        android.util.Log.d("DeviceListActivity", "Showing import dialog for " + devicesFromClipboard.size() + " devices");
                        showImportDevicesDialog(devicesFromClipboard);
                    }
                } else {
                    android.util.Log.d("DeviceListActivity", "Clipboard text is null");
                }
            } else {
                android.util.Log.d("DeviceListActivity", "Clip is null or has no items");
            }
        } else {
            android.util.Log.d("DeviceListActivity", "Clipboard is null or has no primary clip");
        }
    }
    
    private void showImportDevicesDialog(List<String> devices) {
        // Create a dialog to ask user if they want to import the devices
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.import_devices));
        builder.setMessage(getString(R.string.import_devices_message, devices.size()));
        
        builder.setPositiveButton(getString(R.string.import_button), (dialog, which) -> {
            // Import the devices
            importDevicesFromClipboard(devices);
        });
        
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.create().show();
    }
    
    private void importDevicesFromClipboard(List<String> devices) {
        // Add devices to the list
        int addedCount = 0;
        for (String deviceIp : devices) {
            // Check if the device already exists
            boolean exists = false;
            for (DeviceInfo device : deviceList) {
                if (device.getIp().equals(deviceIp)) {
                    exists = true;
                    break;
                }
            }
            
            // If not exists, add it
            if (!exists) {
                deviceList.add(new DeviceInfo(deviceIp, deviceIp));
                addedCount++;
            }
        }
        
        // Save the updated device list
        updateDeviceListInPreferences();
        
        // Update the adapter
        deviceAdapter.updateDevices(deviceList);
        
        // Show success message
        if (addedCount > 0) {
            Toast.makeText(this, getString(R.string.imported_devices_success, addedCount), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.all_devices_exist), Toast.LENGTH_SHORT).show();
        }
    }

    private void initViews() {
        deviceGrid = findViewById(R.id.device_grid);
        addButton = findViewById(R.id.add_device_button);
    }

    private void setupBottomNavigation() {
        Button btnHome = findViewById(R.id.btn_home);
        Button btnDeviceList = findViewById(R.id.btn_device_list);

        btnHome.setOnClickListener(v -> {
            NavigationManager.getInstance().navigateToMain(this);
        });

        btnDeviceList.setOnClickListener(v -> {
            // 已经在设备列表页，无需操作
        });
    }

    private void loadDevices() {
        // Load devices from preferences
        String devicesJson = PreUtils.get(this, Constant.DEVICE_LIST_KEY, "");
        deviceList = new ArrayList<>();
        
        // Parse the JSON string to get actual devices
        if (!devicesJson.isEmpty()) {
            try {
                // Parse JSON array
                org.json.JSONArray jsonArray = new org.json.JSONArray(devicesJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    // Try to parse as object first (new format)
                    try {
                        org.json.JSONObject jsonObject = jsonArray.getJSONObject(i);
                        String name = jsonObject.optString("name", "");
                        String ip = jsonObject.getString("ip");
                        // If name is empty, use IP as name
                        if (name.isEmpty()) {
                            name = ip;
                        }
                        deviceList.add(new DeviceInfo(name, ip));
                    } catch (org.json.JSONException e) {
                        // If it's not an object, try as string (old format)
                        String ip = jsonArray.getString(i);
                        deviceList.add(new DeviceInfo(ip, ip));
                    }
                }
            } catch (org.json.JSONException e) {
                e.printStackTrace();
                // If JSON parsing fails, add mock devices for testing
                deviceList.add(new DeviceInfo("Device 1", "192.168.1.100:5555"));
                deviceList.add(new DeviceInfo("Device 2", "192.168.1.101:5555"));
            }
        }
        
        // Sync with home page history
        syncWithHomePageHistory();
    }
    
    private void syncWithHomePageHistory() {
        // Load home page history
        String homeHistoryJson = PreUtils.get(this, Constant.HISTORY_LIST_KEY, "");
        if (!homeHistoryJson.isEmpty()) {
            try {
                // Parse JSON array
                org.json.JSONArray jsonArray = new org.json.JSONArray(homeHistoryJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    String ip = jsonArray.getString(i);
                    // Check if this IP already exists in device list
                    boolean exists = false;
                    for (DeviceInfo device : deviceList) {
                        if (device.getIp().equals(ip)) {
                            exists = true;
                            break;
                        }
                    }
                    // If not exists, add it with IP as name
                    if (!exists) {
                        deviceList.add(new DeviceInfo(ip, ip));
                    }
                }
                // Update the device list in preferences
                updateDeviceListInPreferences();
            } catch (org.json.JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupAdapter() {
        deviceAdapter = new DeviceAdapter(this, deviceList);
        deviceGrid.setAdapter(deviceAdapter);
    }

    private void setupClickListeners() {
        if (addButton != null) {
            addButton.setOnClickListener(v -> {
                // Show add device dialog
                showAddDeviceDialog();
                // Check clipboard for devices before showing add dialog
                checkClipboardForDevices();
            });
        }

        deviceGrid.setOnItemClickListener((parent, view, position, id) -> {
            DeviceInfo device = deviceList.get(position);
            // Start the main activity with the device info
            Intent intent = new Intent(DeviceListActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.START_REMOTE, true);
            PreUtils.put(DeviceListActivity.this, Constant.CONTROL_REMOTE_ADDR, device.getIp());
            startActivity(intent);
        });

        deviceGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            DeviceInfo device = deviceList.get(position);
            showDeviceOptionsDialog(device, position);
            return true;
        });
    }

    private Dialog createDeviceDialog() { 
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.add_device_dialog);
        return dialog;
    }
    private void showDeviceOptionsDialog(DeviceInfo device, int position) {
        Dialog dialog = createDeviceDialog();

        EditText deviceNameInput = dialog.findViewById(R.id.device_name_input);
        EditText deviceIpInput = dialog.findViewById(R.id.device_ip_input);
        Button confirmButton = dialog.findViewById(R.id.confirm_button);

        // Set existing device info for editing
        deviceNameInput.setText(device.getName());
        deviceIpInput.setText(device.getIp());

        if (confirmButton == null) {
            // If the confirm button doesn't exist in the layout, create it
            confirmButton = new Button(this);
            confirmButton.setText(getString(R.string.save));
            confirmButton.setId(View.generateViewId()); // Generate a unique ID
            confirmButton.setOnClickListener(v -> {
                String deviceName = deviceNameInput.getText().toString().trim();
                String deviceIp = deviceIpInput.getText().toString().trim();

                if (deviceName.isEmpty() || deviceIp.isEmpty()) {
                    Toast.makeText(this, "getString(R.string.fill_all_fields)", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update the device info
                device.setName(deviceName);
                device.setIp(deviceIp);
                
                // Save the updated device list to preferences
                updateDeviceListInPreferences();
                
                // Update the adapter
                deviceAdapter.updateDevices(deviceList);
                
                dialog.dismiss();
                Toast.makeText(this, "getString(R.string.device_updated_successfully)", Toast.LENGTH_SHORT).show();
            });
        } else {
            confirmButton.setText(getString(R.string.save));
            confirmButton.setOnClickListener(v -> {
                String deviceName = deviceNameInput.getText().toString().trim();
                String deviceIp = deviceIpInput.getText().toString().trim();

                if (deviceName.isEmpty() || deviceIp.isEmpty()) {
                    Toast.makeText(this, "getString(R.string.fill_all_fields)", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Update the device info
                device.setName(deviceName);
                device.setIp(deviceIp);
                
                // Save the updated device list to preferences
                updateDeviceListInPreferences();
                
                // Update the adapter
                deviceAdapter.updateDevices(deviceList);
                
                dialog.dismiss();
                Toast.makeText(this, "getString(R.string.device_updated_successfully)", Toast.LENGTH_SHORT).show();
            });
        }

        // Add delete button
        Button deleteButton = new Button(this);
        deleteButton.setText(getString(R.string.delete));
        deleteButton.setId(View.generateViewId());
        deleteButton.setOnClickListener(v -> {
            // Remove the device from the list
            deviceList.remove(position);
            
            // Save the updated device list to preferences
            updateDeviceListInPreferences();
            
            // Update the adapter
            deviceAdapter.updateDevices(deviceList);
            
            dialog.dismiss();
            Toast.makeText(this, "getString(R.string.device_deleted_successfully)", Toast.LENGTH_SHORT).show();
        });

        // Add delete button to the dialog
        View contentView = dialog.getWindow().getDecorView().findViewById(android.R.id.content);
        if (contentView instanceof ViewGroup) {
            ViewGroup contentViewGroup = (ViewGroup) contentView;
            if (contentViewGroup.getChildCount() > 0) {
                View childView = contentViewGroup.getChildAt(0);
                if (childView instanceof LinearLayout) {
                    LinearLayout dialogLayout = (LinearLayout) childView;
                    // Remove the existing confirm button
                    View existingConfirmButton = dialogLayout.findViewById(R.id.confirm_button);
                    if (existingConfirmButton != null) {
                        dialogLayout.removeView(existingConfirmButton);
                    }
                    
                    // Add buttons to dialog layout
                    LinearLayout buttonLayout = new LinearLayout(this);
                    buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
                    buttonLayout.setPadding(0, 20, 0, 0);
                    
                    confirmButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    deleteButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    
                    buttonLayout.addView(confirmButton);
                    buttonLayout.addView(deleteButton);
                    
                    dialogLayout.addView(buttonLayout);
                }
            }
        }

        dialog.show();
        
        // Show keyboard
        deviceNameInput.post(() -> {
            deviceNameInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });
    }

    private void showAddDeviceDialog() {
        Dialog dialog = createDeviceDialog();

        EditText deviceNameInput = dialog.findViewById(R.id.device_name_input);
        EditText deviceIpInput = dialog.findViewById(R.id.device_ip_input);
        Button confirmButton = dialog.findViewById(R.id.confirm_button);

        if (confirmButton == null) {
            // If the confirm button doesn't exist in the layout, create it
            confirmButton = new Button(this);
            confirmButton.setText(getString(R.string.confirm));
            confirmButton.setId(View.generateViewId()); // Generate a unique ID
            confirmButton.setOnClickListener(v -> {
                String deviceName = deviceNameInput.getText().toString().trim();
                String deviceIp = deviceIpInput.getText().toString().trim();

                if (deviceName.isEmpty() || deviceIp.isEmpty()) {
                    Toast.makeText(this, "getString(R.string.fill_all_fields)", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Add the new device to the list
                DeviceInfo newDevice = new DeviceInfo(deviceName, deviceIp);
                deviceList.add(0, newDevice); // Add to the beginning of the list
                
                // Save the device to preferences (in a real app, you'd save properly)
                updateDeviceListInPreferences();
                
                // Update the adapter
                deviceAdapter.updateDevices(deviceList);
                
                dialog.dismiss();
                Toast.makeText(this, "getString(R.string.device_added_successfully)", Toast.LENGTH_SHORT).show();
            });
        } else {
            confirmButton.setOnClickListener(v -> {
                String deviceName = deviceNameInput.getText().toString().trim();
                String deviceIp = deviceIpInput.getText().toString().trim();

                if (deviceName.isEmpty() || deviceIp.isEmpty()) {
                    Toast.makeText(this, "getString(R.string.fill_all_fields)", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Add the new device to the list
                DeviceInfo newDevice = new DeviceInfo(deviceName, deviceIp);
                deviceList.add(0, newDevice); // Add to the beginning of the list
                
                // Save the device to preferences (in a real app, you'd save properly)
                updateDeviceListInPreferences();
                
                // Update the adapter
                deviceAdapter.updateDevices(deviceList);
                
                dialog.dismiss();
                Toast.makeText(this, "getString(R.string.device_added_successfully)", Toast.LENGTH_SHORT).show();
            });
        }

        dialog.show();
        
        // Show keyboard
        deviceNameInput.post(() -> {
            deviceNameInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });
    }

    private void updateDeviceListInPreferences() {
        // Save the device list to preferences as JSON
        StringBuilder historyList = new StringBuilder("[");
        StringBuilder homeHistoryList = new StringBuilder("[");
        for (int i = 0; i < deviceList.size(); i++) {
            if (i > 0) {
                historyList.append(",");
                homeHistoryList.append(",");
            }
            historyList.append("{\"name\":\"").append(deviceList.get(i).getName()).append("\",\"ip\":\"").append(deviceList.get(i).getIp()).append("\"}");
            homeHistoryList.append("\"").append(deviceList.get(i).getIp()).append("\"");
        }
        historyList.append("]");
        homeHistoryList.append("]");
        PreUtils.put(this, Constant.DEVICE_LIST_KEY, historyList.toString());
        // Also update home page history to keep in sync
        PreUtils.put(this, Constant.HISTORY_LIST_KEY, homeHistoryList.toString());
    }

    private void startScreenshotUpdates() {
        handler = new Handler(Looper.getMainLooper());
        screenshotRunnable = new Runnable() {
            @Override
            public void run() {
                updateScreenshots();
                // Schedule the next update in 10 seconds
                handler.postDelayed(this, 10000);
            }
        };
        handler.post(screenshotRunnable);
    }

    private void updateScreenshots() {
        // Get visible items range
        int firstVisible = deviceGrid.getFirstVisiblePosition();
        int lastVisible = deviceGrid.getLastVisiblePosition();
        // Update screenshots only for visible devices
        if (firstVisible <= lastVisible) {
            // Update screenshots and connection status for each visible device in a background thread
            new Thread(() -> {
                for (int i = firstVisible; i <= lastVisible; i++) {
                    if (i < deviceList.size()) {
                        final DeviceInfo device = deviceList.get(i);
                        final int position = i;
                        
                        // Parse IP and port from the device IP string (format: "ip:port")
                        String[] ipPort = device.getIp().split(":");
                        if (ipPort.length == 2) {
                            String ip = ipPort[0];
                            int port = Integer.parseInt(ipPort[1]);
                            
                            // Check if the device is reachable
                            boolean isReachable = ScrcpyClient.isDeviceReachable(ip, port);
                            device.setConnected(isReachable);
                            
                            // First update connection status immediately
                            runOnUiThread(() -> {
                                // Update the connection status for this device
                                deviceAdapter.notifyDataSetChanged();
                            });
                            
                            // Get screenshot if device is reachable (this may take time)
                            if (isReachable) {
                                String screenshotPath = ScrcpyClient.getScreenshotFromDevice(ip, port);
                                if (screenshotPath != null) {
                                    // Update the device's last screenshot path
                                    device.setLastScreenshotPath(screenshotPath);
                                    
                                    // Update screenshot preview after getting the screenshot
                                    runOnUiThread(() -> {
                                        // Update the screenshot for this device
                                        deviceAdapter.notifyDataSetChanged();
                                    });
                                }
                            } else {
                                // Device not reachable, clear screenshot
                                device.setLastScreenshotPath(null);
                                runOnUiThread(() -> {
                                    // Update the screenshot for this device
                                    deviceAdapter.notifyDataSetChanged();
                                });
                            }
                        } else {
                            // Invalid IP format, set to not connected
                            device.setConnected(false);
                            device.setLastScreenshotPath(null);
                            
                            // Update UI for invalid IP
                            runOnUiThread(() -> {
                                // Update the device status
                                deviceAdapter.notifyDataSetChanged();
                            });
                        }
                    }
                }
            }).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop screenshot updates when the app is in background
        if (handler != null && screenshotRunnable != null) {
            handler.removeCallbacks(screenshotRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume screenshot updates when the app is in foreground
        if (handler != null && screenshotRunnable != null) {
            handler.post(screenshotRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove the screenshot update callback to prevent memory leaks
        if (handler != null && screenshotRunnable != null) {
            handler.removeCallbacks(screenshotRunnable);
        }
    }
}