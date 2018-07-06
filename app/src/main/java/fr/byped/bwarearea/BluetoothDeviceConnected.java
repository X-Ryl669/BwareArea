package fr.byped.bwarearea;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class BluetoothDeviceConnected extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        // Check if we have to start on BT
        String devName = pref.getString("btTrigger", "");
        if (devName.isEmpty()) return;

        // Seems so, so check if the given device should be started
        String action = intent.getAction();
        BluetoothDevice device = (BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (!device.getName().equals(devName)) return;

        // Ok, it's the right device, so let's start/stop the service now
        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED))
        {
            // Start service now
            context.startService(new Intent(context, FloatingWarnerService.class));
        }
        else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED))
        {
            // Stop service
            context.stopService(new Intent(context, FloatingWarnerService.class));
        }
    }
}
