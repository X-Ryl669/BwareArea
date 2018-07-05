package fr.byped.bwarearea;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private Button startService;
    private Button importDB;
    private TextView POIDBLabel;
    private SharedPreferences pref;



    private static final int GET_FILE = 124;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        askForSystemOverlayPermission();

        pref = getSharedPreferences("settings", Context.MODE_PRIVATE);


        POIDBLabel = (TextView)findViewById(R.id.POIDBLabel);
        progressBar = (ProgressBar)findViewById(R.id.importProgress);
        SeekBar bar = (SeekBar) findViewById(R.id.distance);
        final TextView distance = (TextView)findViewById(R.id.distanceLabel);
        final int curDistance = pref.getInt("distance", 300);
        bar.setMax(1000);
        bar.setProgress(curDistance);
        distance.setText(String.format(getString(R.string.base_distance), curDistance));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
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
        startService = (Button) findViewById(R.id.startStop);
        importDB = (Button)findViewById(R.id.POIDBLoad);
//        textView = (TextView) findViewById(R.id.textView);


  //      int badge_count = getIntent().getIntExtra("badge_count", 0);

//        textView.setText(badge_count + " messages received previously");

        importDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (MainActivity.this.isReadStoragePermissionGranted())
                    selectFileToImport();
                // Else, it'll be run while the permission is granted asynchronously
            }
        });

        startService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(MainActivity.this)) {
                    startService(new Intent(MainActivity.this, FloatingWarnerService.class));
                    Toast.makeText(getApplicationContext(), R.string.started_service, Toast.LENGTH_LONG).show();
                    MainActivity.this.finish();
                } else {
                    errorToast();
                }
            }
        });
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
            String currFileURI = data.getData().getPath();
            String[] lines = SpeedcamParser.getLinesFromSpeedcamFile(this, currFileURI);
            if (lines != null)
                new ImportTask(this, lines).execute(lines.length);

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

    public  boolean isReadStoragePermissionGranted() {
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
        if (requestCode == 3)
        {
            Log.d("BwareArea", "External storage1");
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                Log.v("BwareArea","Permission: "+ permissions[0] + "was " + grantResults[0]);
                selectFileToImport();
            }
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

