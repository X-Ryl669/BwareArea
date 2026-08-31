package fr.byped.bwarearea;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.content.ContextCompat;

public class BluetoothDeviceConnected extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        // Check if we have to start on BT
        String devName = pref.getString("btTrigger", "");
        if (devName.isEmpty()) return;

        String action = intent.getAction();
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null || device.getName() == null) return;  // fix: NPE protection
        if (!device.getName().equals(devName)) return;

        // Ok, it's the right device, so let's start/stop the service now
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            // Android 8+: must use startForegroundService from background
            Intent serviceIntent = new Intent(context, FloatingWarnerService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            // Stop service
            context.stopService(new Intent(context, FloatingWarnerService.class));
        }
    }
}