package org.client.scrcpy.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import com.bumptech.glide.Glide;

import org.client.scrcpy.R;
import org.client.scrcpy.model.DeviceInfo;

import java.util.List;

public class DeviceAdapter extends BaseAdapter {
    private static final String TAG = "DeviceAdapter";
    private Context context;
    private List<DeviceInfo> devices;
    private LayoutInflater inflater;

    public DeviceAdapter(Context context, List<DeviceInfo> devices) {
        this.context = context;
        this.devices = devices;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.device_list_item, parent, false);
            holder = new ViewHolder();
            holder.devicePreview = convertView.findViewById(R.id.device_preview);
            holder.deviceName = convertView.findViewById(R.id.device_name);
            holder.tvDaysRemaining = convertView.findViewById(R.id.tv_days_remaining);
            holder.connectionStatus = convertView.findViewById(R.id.connection_status);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        DeviceInfo device = devices.get(position);
        holder.deviceName.setText(device.getName());
        
        // 显示剩余天数
        if (holder.tvDaysRemaining != null) {
            int days = device.getDaysRemaining();
            if (days > 0) {
                holder.tvDaysRemaining.setText("剩余" + days + "天");
                holder.tvDaysRemaining.setTextColor(0xFF00FF00); // 绿色
            } else if (days == 0) {
                holder.tvDaysRemaining.setText("今天到期");
                holder.tvDaysRemaining.setTextColor(0xFFFFFF00); // 黄色
            } else {
                holder.tvDaysRemaining.setText("已过期");
                holder.tvDaysRemaining.setTextColor(0xFFFF0000); // 红色
            }
            holder.tvDaysRemaining.setVisibility(View.VISIBLE);
        }
        
        // Update connection status indicator based on device's isConnected field
        if (holder.connectionStatus != null) {
            // Set color based on connection status: green for connected, red for not
            int color = device.isConnected() ? 0xFF00FF00 : 0xFFFF0000;
            // Set the color of the circular indicator
            Drawable background = holder.connectionStatus.getBackground();
            if (background != null) {
                background.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }

        // Load screenshot if available
        if (device.getLastScreenshotPath() != null && !device.getLastScreenshotPath().isEmpty()) {
             Glide.with(context)
                        .load(device.getLastScreenshotPath())
                        .override(320, 240) // Further reduce image size
                        .centerCrop() // Crop image to fit
                        .thumbnail(0.3f) // Smaller thumbnail
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(holder.devicePreview);
        } else {
            Log.d(TAG, "position " + position + " No screenshot available.");
            // Use a default placeholder if no screenshot is available
            holder.devicePreview.setImageResource(R.drawable.ic_launcher_foreground);
        }

        return convertView;
    }

   private static class ViewHolder {
        ImageView devicePreview;
        TextView deviceName;
        TextView tvDaysRemaining;
        View connectionStatus;
    }

    public void updateDevices(List<DeviceInfo> newDevices) {
        this.devices = newDevices;
        Log.d(TAG, "Updating devices: " + newDevices.size());
        notifyDataSetChanged();
    }
}