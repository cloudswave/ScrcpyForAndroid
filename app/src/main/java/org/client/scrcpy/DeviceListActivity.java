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
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.client.scrcpy.adapter.DeviceAdapter;
import org.client.scrcpy.api.ApiClient;
import org.client.scrcpy.model.DeviceInfo;
import org.client.scrcpy.utils.PreUtils;
import org.client.scrcpy.ScrcpyClient;
import org.client.scrcpy.navigation.NavigationManager;

import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends Activity {

    private GridView deviceGrid;
    private SwipeRefreshLayout swipeRefreshLayout;
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
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        
        // 设置下拉刷新
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
            );
            swipeRefreshLayout.setOnRefreshListener(() -> {
                // 刷新设备列表
                loadDevices();
                // 停止刷新动画
                swipeRefreshLayout.postDelayed(() -> {
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }, 1000);
            });
        }
        
        addButton = findViewById(R.id.add_device_button);
        
        // 显示用户名
        TextView tvUsername = findViewById(R.id.tv_username);
        ApiClient apiClient = ApiClient.getInstance(this);
        if (tvUsername != null && apiClient.isLoggedIn()) {
            tvUsername.setText(apiClient.getUsername());
        }
        
        // 退出登录按钮
        ImageButton btnLogout = findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("退出登录")
                    .setMessage("确定要退出登录吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        ApiClient.getInstance(this).logout();
                        // 跳转到首页并显示登录弹框
                        Intent intent = new Intent(DeviceListActivity.this, MainActivity.class);
                        intent.putExtra("show_login_dialog", true);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            });
        }
    }
    
    /**
     * 显示登录弹框
     */
    private void showLoginDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_settings, null);
        
        // 隐藏服务器地址输入
        dialogView.findViewById(R.id.tv_server_url).setVisibility(View.GONE);
        dialogView.findViewById(R.id.et_server_url).setVisibility(View.GONE);
        
        EditText etUsername = dialogView.findViewById(R.id.et_username);
        EditText etPassword = dialogView.findViewById(R.id.et_password);
        Button btnLogin = dialogView.findViewById(R.id.btn_login);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("登录")
            .setView(dialogView)
            .setCancelable(false)
            .create();
        
        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            
            btnLogin.setEnabled(false);
            btnLogin.setText("登录中...");
            
            new Thread(() -> {
                try {
                    ApiClient.getInstance(this).login(username, password);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // 刷新列表
                        loadDevices();
                        // 更新用户名显示
                        TextView tvUsername = findViewById(R.id.tv_username);
                        if (tvUsername != null) {
                            tvUsername.setText(ApiClient.getInstance(this).getUsername());
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("登录");
                        Toast.makeText(this, "登录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
        
        dialog.show();
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
        // 先加载本地设备列表
        loadLocalDevices();
        
        // 然后从 API 获取远程设备列表并融合
        loadRemoteDevices();
        
        // Sync with home page history
        syncWithHomePageHistory();
    }
    
    private void loadLocalDevices() {
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
                        String screenshot = jsonObject.optString("screenshot", null);
                        String ip = jsonObject.getString("ip");
                        // If name is empty, use IP as name
                        if (name.isEmpty()) {
                            name = ip;
                        }
                        DeviceInfo device = new DeviceInfo(name, ip);
                        if (screenshot != null && !screenshot.isEmpty()) {
                            device.setLastScreenshotPath(screenshot);
                        }
                        deviceList.add(device);
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
    }
    
    private void loadRemoteDevices() {
        // 从 API 获取远程设备
        ApiClient apiClient = ApiClient.getInstance(this);
        
        if (!apiClient.isLoggedIn() || !apiClient.hasServerUrl()) {
            return;
        }
        
        new Thread(() -> {
            try {
                List<ApiClient.Device> remoteDevices = apiClient.getDevices();
                
                runOnUiThread(() -> {
                    // 收集远程设备serial列表
                    List<String> remoteSerials = new ArrayList<>();
                    for (ApiClient.Device remoteDevice : remoteDevices) {
                        remoteSerials.add(remoteDevice.deviceSerial);
                    }
                    
                    // 删除本地已删除的设备（远程不存在的设备）
                    Iterator<DeviceInfo> iterator = deviceList.iterator();
                    while (iterator.hasNext()) {
                        DeviceInfo localDevice = iterator.next();
                        // 只删除有serial的本地设备（来自远程的设备）
                        if (!remoteSerials.contains(localDevice.getIp())) {
                            // 检查是否是本地手动添加的设备（IP不含端口的可能是本地的）
                            // 为简单起见，只要远程不存在的都删除
                            iterator.remove();
                        }
                    }
                    
                    // 更新或添加远程设备
                    for (ApiClient.Device remoteDevice : remoteDevices) {
                        // 检查是否已存在
                        boolean exists = false;
                        for (DeviceInfo localDevice : deviceList) {
                            if (localDevice.getIp().equals(remoteDevice.deviceSerial)) {
                                // 已存在设备也同步更新过期时间
                                localDevice.setDaysRemaining(remoteDevice.getDaysRemaining());
                                localDevice.setExpiresAt(remoteDevice.expiresAt);
                                exists = true;
                                break;
                            }
                        }
                        // 不存在则添加
                        if (!exists) {
                            DeviceInfo deviceInfo = new DeviceInfo(remoteDevice.deviceName, remoteDevice.deviceSerial);
                            deviceInfo.setDaysRemaining(remoteDevice.getDaysRemaining());
                            deviceInfo.setExpiresAt(remoteDevice.expiresAt);
                            deviceList.add(deviceInfo);
                        }
                    }
                    
                    // 保存更新后的设备列表
                    updateDeviceListInPreferences();
                    
                    // 刷新列表
                    if (deviceAdapter != null) {
                        deviceAdapter.notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("DeviceListActivity", "Load remote devices failed", e);
            }
        }).start();
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
                showRedeemDeviceDialog();
            });
        }

        deviceGrid.setOnItemClickListener((parent, view, position, id) -> {
            DeviceInfo device = deviceList.get(position);
            Intent intent = new Intent(DeviceListActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.START_REMOTE, true);
            PreUtils.put(DeviceListActivity.this, Constant.CONTROL_REMOTE_ADDR, device.getIp());
            startActivity(intent);
        });
        
        // 移除长按事件
    }
    
    /**
     * 显示兑换设备弹框
     */
    private void showRedeemDeviceDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_redeem, null);
        EditText etCode = dialogView.findViewById(R.id.et_redeem_code);
        Button btnRedeem = dialogView.findViewById(R.id.btn_redeem);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .create();
        
        btnRedeem.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "请输入兑换码", Toast.LENGTH_SHORT).show();
                return;
            }
            
            btnRedeem.setEnabled(false);
            btnRedeem.setText("兑换中...");
            
            redeemDevice(code, dialog, btnRedeem);
        });
        
        dialog.show();
    }
    
    /**
     * 兑换设备
     */
    private void redeemDevice(String code, AlertDialog dialog, Button btnRedeem) {
        ApiClient apiClient = ApiClient.getInstance(this);
        
        new Thread(() -> {
            try {
                apiClient.activateDevice(code);
                runOnUiThread(() -> {
                    Toast.makeText(this, "兑换成功", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    // 刷新设备列表
                    loadDevices();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnRedeem.setEnabled(true);
                    btnRedeem.setText("兑换");
                    Toast.makeText(this, "兑换失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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
            String screenshotPath = deviceList.get(i).getLastScreenshotPath();
                if (screenshotPath != null && !screenshotPath.isEmpty()) {
                    historyList.append("{\"name\":\"").append(deviceList.get(i).getName()).append("\",\"ip\":\"").append(deviceList.get(i).getIp()).append("\",\"screenshot\":\"").append(screenshotPath).append("\"}");
                } else {
                    historyList.append("{\"name\":\"").append(deviceList.get(i).getName()).append("\",\"ip\":\"").append(deviceList.get(i).getIp()).append("\"}");
                }
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

                                // Save screenshot path to preferences immediately
                                runOnUiThread(() -> {
                                    updateDeviceListInPreferences();
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