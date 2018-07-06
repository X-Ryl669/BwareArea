package fr.byped.bwarearea;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private Button startService;
    private Button importDB;
    private Switch bluetooth;
    private TextView POIDBLabel;
    private SharedPreferences pref;

    // Our handler for received Intents. This will be called whenever an Intent
    // with an action named "finish_activity" is broadcasted.
    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            // Finish the activity
            MainActivity.this.finish();
        }
    };


    private static final int GET_FILE = 124;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        pref = getSharedPreferences("settings", Context.MODE_PRIVATE);


        POIDBLabel = (TextView)findViewById(R.id.POIDBLabel);
        POIDBLabel.setText(String.format(getString(R.string.point_of_interest_database_with_poi), (int)pref.getLong("poiCount", 0)));

        progressBar = (ProgressBar)findViewById(R.id.importProgress);


        // Limit for country were it's illegal to give the exact position
        final Switch range = (Switch)findViewById(R.id.limitRange);
        range.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean("onlyRange", range.isChecked());
                editor.apply();
            }
        });

        // Start when some bluetooth device is in range
        bluetooth = (Switch)findViewById(R.id.bluetoothStart);
        String btDev = pref.getString("btTrigger", "");
        bluetooth.setChecked(!btDev.isEmpty());
        bluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetooth.isChecked())
                {
                    // Tell the user to connect to the bluetooth device we need to recognize and use that.
                    boolean device = getCurrentlyConnectedDevice();
                    if (!device) {
                        Toast.makeText(MainActivity.this, R.string.need_bluetooth_connected, Toast.LENGTH_LONG).show();
                        bluetooth.setChecked(false);
                    }
                } else
                {
                    setBluetoothDeviceToUse("");
                }
            }
        });
        setBluetoothDeviceToUse(btDev);

        SeekBar bar = (SeekBar) findViewById(R.id.distance);
        final TextView distance = (TextView)findViewById(R.id.distanceLabel);
        final int curDistance = pref.getInt("distance", 300);
        bar.setMax(1000);
        bar.incrementProgressBy(10);
        bar.setProgress(curDistance);
        distance.setText(String.format(getString(R.string.base_distance), curDistance));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                i = (i / 10) * 10;
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt("distance", i);
                editor.apply();

                distance.setText(String.format(getString(R.string.base_distance), i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Overspeed
        SeekBar os = (SeekBar) findViewById(R.id.overspeed);
        final TextView overspeedLabel = (TextView)findViewById(R.id.alertOverspeed);
        final int curAlert = pref.getInt("overspeed", 5);
        os.setMax(50);
        os.incrementProgressBy(5);
        os.setProgress(curAlert);

        overspeedLabel.setText(String.format(getString(R.string.base_overspeed), curAlert));
        os.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                i = (i / 5) * 5;
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt("overspeed", i);
                editor.apply();

                overspeedLabel.setText(String.format(getString(R.string.base_overspeed), i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Database stuff below
        importDB = (Button)findViewById(R.id.POIDBLoad);
        importDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (MainActivity.this.isReadStoragePermissionGranted())
                    selectFileToImport();
                // Else, it'll be run while the permission is granted asynchronously
            }
        });

        // Service starting here
        startService = (Button) findViewById(R.id.startStop);
        startService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.askForSystemOverlayPermission();

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(MainActivity.this)) {
                    if (isLocationFetchingPermissionGranted())
                        startService();
                } else {
                    errorToast();
                }
            }
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("finish_activity"));
    }

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        super.onDestroy();
    }

    public void setBluetoothDeviceToUse(String name)
    {
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("btTrigger", name);
        editor.apply();


        if (name.isEmpty())
            bluetooth.setText(getString(R.string.bluetooth_trigger));
        else bluetooth.setText(String.format(getString(R.string.bluetooth_device_label), name));
    }

    public boolean getCurrentlyConnectedDevice() {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()
         || adapter.getProfileConnectionState(BluetoothHeadset.HEADSET) != BluetoothHeadset.STATE_CONNECTED)
            return false;

        // This is very poor way to detect which one is connected, but I don't know any other
        // This will trigger the connected device later on
        BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener()
        {
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HEADSET) {
                    BluetoothHeadset hs = (BluetoothHeadset) proxy;
                    List<BluetoothDevice> hsConnectedDevices = hs.getConnectedDevices();
                    if (hsConnectedDevices.size() != 0) {
                        for (BluetoothDevice device : hsConnectedDevices) {
                            setBluetoothDeviceToUse(device.getName());
                            break;
                        }
                    }
                    adapter.closeProfileProxy(BluetoothProfile.HEADSET, hs);
                }
            }

            public void onServiceDisconnected(int profile) {

            }
        };
        // This is unfortunately asynchronous, so let's figure this out later
        return adapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);
    }

    private static final int DRAW_OVER_OTHER_APP_PERMISSION = 123;
    private void askForSystemOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))
        {
            // If the draw over permission is not available to open the settings screen
            // to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, DRAW_OVER_OTHER_APP_PERMISSION);
        }
    }

    private static final int GET_GPS_POS = 125;
    private boolean isLocationFetchingPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED)
            {
                Log.v("BwareArea","Fine location permission granted");
                return true;
            } else {
                Log.v("BwareArea","Fine location permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, GET_GPS_POS);
                return false;
            }
        }
        else { // permission is automatically granted on sdk<23 upon installation
            Log.v("BwareArea","Fine location permission granted");
            return true;
        }
    }

    /** Start the main service and finish this activity */
    private void startService()
    {
        startService(new Intent(MainActivity.this, FloatingWarnerService.class));
        Toast.makeText(getApplicationContext(), R.string.started_service, Toast.LENGTH_LONG).show();
//        MainActivity.this.finish();
    }



    @Override
    @TargetApi(23)
    protected void onPause()
    {
        super.onPause();
//        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DRAW_OVER_OTHER_APP_PERMISSION) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    // Permission is not available. Display error text.
                    errorToast();
                    finish();
                }
            }
        } else if (requestCode == GET_FILE)
        {
            if (data == null) return;
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());

                String[] lines = SpeedcamParser.getLinesFromStream(this, is);
                if (lines != null)
                    new ImportTask(this, lines).execute(lines.length);
            } catch (FileNotFoundException e)
            {
                Log.e("Bware", "File not found : "+ e.getMessage());

            }
/*
            SharedPreferences.Editor editor = pref.edit();
            editor.putString("importFile", currFileURI);
            editor.apply();

            // Stop any running service
            stopService(new Intent(this, FloatingWarnerService.class));
            // Let it run, so it can import it
            startService(new Intent(this, FloatingWarnerService.class));
            */
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public boolean isReadStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED)
            {
                Log.v("BwareArea","Read storage permission granted");
                return true;
            } else {
                Log.v("BwareArea","Read storage Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, 3);
                return false;
            }
        }
        else { // permission is automatically granted on sdk<23 upon installation
            Log.v("BwareArea","Read permission is granted");
            return true;
        }
    }

    void selectFileToImport()
    {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        MainActivity.this.startActivityForResult(Intent.createChooser(intent, getText(R.string.select_file)),GET_FILE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case 3:
                Log.d("BwareArea", "External storage");
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.v("BwareArea","Permission: "+ permissions[0] + "was " + grantResults[0]);
                    selectFileToImport();
                }
                break;
            case GET_GPS_POS:
                Log.d("BwareArea", "GPS fetching");
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    Log.v("BwareArea","Permission: "+ permissions[0] + "was " + grantResults[0]);
                    startService();
                }
                break;
            default: break;
        }
    }

    private void errorToast() {
        Toast.makeText(this, "Draw over other app permission not available. Can't start the application without the permission.", Toast.LENGTH_LONG).show();
    }


    private ProgressBar progressBar;

    Integer poiCount = 0;
    class ImportTask extends AsyncTask<Integer, Integer, String> 
    {
        POICollection collection;
        SpeedcamParser parser;
        String[] lines;
        @Override
        protected String doInBackground(Integer... params)
        {
            for (; poiCount <= params[0]; poiCount += 10) {
                try {
                    parser.importFromLines(lines, poiCount, 10 );
                    publishProgress(poiCount);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            SharedPreferences.Editor editor = pref.edit();
            editor.putLong("poiCount", parser.lastPOICount);
            editor.apply();
            return getString(R.string.import_completed);
        }
        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            POIDBLabel.setText(result);

        }
        @Override
        protected void onPreExecute() {
            poiCount = 0;
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            POIDBLabel.setText(R.string.import_starting);
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            POIDBLabel.setText(String.format(getString(R.string.import_x_poi), values[0]));
            progressBar.setProgress(values[0]);
        }

        ImportTask(Context context, String[] lines)
        {
            this.lines = lines;
            collection = new POICollection(context);
            progressBar.setMax(lines.length);
            parser = new SpeedcamParser(collection);

        }
    }

}

