package fr.byped.bwarearea;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Locale;

public class FloatingWarnerService extends Service {
    private WindowManager mWindowManager;
    private View mOverlayView;
    private FloatingWidget widgetContainer;
    private POICollection collection;
    private SharedPreferences pref;
    private Binder binder;
    private LocationManager locationManager;
    private BwareLocationListener locListener;
    private int poiCount;
    private FileWriter logToFile;
    private boolean trackOpened;



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    private void showLocationNotification()
    {
        Intent intent = new Intent("finish_service");
        intent.setClass(this, FloatingWarnerService.class);
        // You can also include some extra data.
        intent.putExtra("message", "From service!");
        Notification notification = new NotificationCompat.Builder(this, "main")
                .setContentTitle(getString(R.string.bware_is_running))
                .setContentText(getString(R.string.tap_to_settings))
                .setSmallIcon(R.mipmap.ic_launcher_bware)
                .setContentIntent(PendingIntent.getService(this, 0, intent, 0))
                .setOngoing(true)
                .build();



        startForeground(1, notification);

    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        if (intent.getAction() == "finish_service")
        {
            stopCleanly();
            // And restart the activity
            startActivity(new Intent(this, MainActivity.class));
        }
        return Service.START_STICKY;
    }

    @SuppressLint("all")
    private int getOverlayType()
    {
        int typePhone = 2002; // This is poor man escape for deprecation warning
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ? typePhone : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();
        binder = new Binder();

        // Check if we have some action to perform first
        collection = new POICollection(this);
        pref = getSharedPreferences("settings", Context.MODE_PRIVATE);
        poiCount = (int)pref.getLong("poiCount", 0);
        trackOpened = false;

        setTheme(R.style.AppTheme);

        mOverlayView = LayoutInflater.from(this).inflate(R.layout.floating_warner_widget, null);



        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);


        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.START;        //Initially view will be added to top-left corner
        params.x = 0;
        params.y = 100;


        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mOverlayView, params);

        widgetContainer = mOverlayView.findViewById(R.id.widgetContainer);
        widgetContainer.bindAll(mOverlayView);
        widgetContainer.setRangeAndAlertAndWarnDistance(pref.getBoolean("onlyRange", false), pref.getInt("distance", 300), pref.getInt("overspeed", 5));
        widgetContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            /** This is the basic double tap to zoom/dezoom function implementation */
            class GestureListener extends GestureDetector.SimpleOnGestureListener {
                @Override
                public boolean onDoubleTap(final MotionEvent e) {
                    // Should trigger our main activity and stop the service
                    Intent intent = new Intent(FloatingWarnerService.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);

                    //close the service and remove the fab view
                    stopCleanly();
                    return true;
                }
            }

            private GestureDetector gestureDetector = new GestureDetector(FloatingWarnerService.this, new GestureListener());

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Capture double tap
                if (gestureDetector.onTouchEvent(event)) return true;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        //remember the initial position.
                        initialX = params.x;
                        initialY = params.y;


                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();


                        return true;
                    case MotionEvent.ACTION_UP:

                        // Add code for launching application and positioning the widget to nearest edge.


                        return true;
                    case MotionEvent.ACTION_MOVE:


                        float Xdiff = Math.round(event.getRawX() - initialTouchX);
                        float Ydiff = Math.round(event.getRawY() - initialTouchY);


                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) Xdiff;
                        params.y = initialY + (int) Ydiff;

                        //Update the layout with new X & Y coordinates
                        mWindowManager.updateViewLayout(mOverlayView, params);


                        return true;
                }
                return false;
            }
        });

        // We need to create the VP Tree from all the POI so let's do it now
//        if(android.os.Debug.isDebuggerConnected()) poiCount = 100;
        new StartService(widgetContainer, collection, this).execute(poiCount);

        Intent intent = new Intent("finish_activity");
        // You can also include some extra data.
        intent.putExtra("message", "From service!");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // Create log file if required
        if (pref.getBoolean("logFile", false))
        {
            try {
                File sdFolder = new File(Environment.getExternalStorageDirectory(), "Bware");
//                File sdFolder = new File(getFilesDir(), "Bware");
                if (!sdFolder.exists()) sdFolder.mkdir();
                logToFile = new FileWriter(new File(sdFolder, String.format("track_%d.gpx", Calendar.getInstance().getTime().getTime())));
                logToFile.write("<?xml version='1.0' encoding='Utf-8' standalone='yes' ?>\n<gpx xmlns=\"http://www.topografix.com/GPX/1/0\" version=\"1.0\" creator=\"fr.byped.bwarearea\">\n");
            } catch (Exception e) {
                Log.e("Bware", "Got exception while creating writer: " + e.getMessage());
                logToFile = null;
            }
        }
    }

    @Override
    public void onDestroy() {
        stopLocation();
        super.onDestroy();
        if (mOverlayView != null)
            mWindowManager.removeView(mOverlayView);
    }
/*
    public static Thread performOnBackgroundThread(final Runnable runnable) {
        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } finally {

                }
            }
        };
        t.start();
        return t;
    }
*/

    public FloatingWarnerService() {
    }

    private void stopLocation() {
        if (locationManager != null) {
            locationManager.removeUpdates(locListener);
            locListener = null;
            locationManager = null;
        }
        if (logToFile != null) {
            try {
                if (trackOpened) logToFile.append("</trkseg></trk>\n");
                trackOpened = false;
                logToFile.append("</gpx>\n");
                logToFile.close();
            } catch(IOException e)
            {
                Log.e("Bware", "Error while writing footer to gpx file: " + e.getMessage());
            }
            logToFile = null;
        }
    }

    private void stopCleanly() {
        stopLocation();
        stopSelf();
    }

    /** Location stuff below */
    private void doneImporting()
    {
        FloatingWarnerService.this.showLocationNotification();

        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || locationManager.getAllProviders().isEmpty())
        {
            Toast.makeText(this, R.string.cant_get_location_manager, Toast.LENGTH_LONG).show();
            stopCleanly();
            return;
        }
        try {
//            String gpsProvider = locationManager.getProvider(locationManager.GPS_PROVIDER);
//            for (String provider : locationManager.getAllProviders()) {
                Log.i("Bware", "provider " + LocationManager.GPS_PROVIDER);
                locListener = new BwareLocationListener(collection, widgetContainer);
                Location loc = locationManager.getLastKnownLocation(locationManager.GPS_PROVIDER);
                Log.i("Bware", "Initializing with loc: " + loc);
                // search updated location
               // onLocationChanged(locationManager.getLastKnownLocation(locationManager.GPS_PROVIDER));
               // locationManager.requestLocationUpdates(locationManager.GPS_PROVIDER, 2000, 50, this);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locListener);
  //          }
        } catch(SecurityException e)
        {
            Log.e("Bware", "Security exception while registering GPS precision location: " + e.getMessage());
            Toast.makeText(this, R.string.cant_get_location_manager, Toast.LENGTH_LONG).show();
            stopCleanly();
        }

    }

    public class BaseCoord implements Coordinate
    {
        Location loc;
        BaseCoord(Location loc) { this.loc = loc; }

        @Override
        public double getLatitude() {
            return loc.getLatitude();
        }

        @Override
        public double getLongitude() {
            return loc.getLongitude();
        }

        public float speed() { return loc.getSpeed(); }
    }



    public class Binder extends android.os.Binder {
        public FloatingWarnerService getService() {
            return FloatingWarnerService.this;
        }
    }



    private static class StartService extends AsyncTask<Integer, Integer, String>
    {
        FloatingWidget                               widget;
        POICollection                                collection;
        private WeakReference<FloatingWarnerService> service;
        int                                          poiCount;


        @Override
        protected String doInBackground(Integer... params)
        {
            for (int i = 0; i <= params[0]; i += 10) {
                try {
                    collection.buildVPTreeIteratively(10);
                    publishProgress(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return "Task Completed.";
        }
        @Override
        protected void onPostExecute(String result) {
            collection.finishVPTreeIterativeBuild();
            widget.doneImporting();
            service.get().doneImporting();
        }
        @Override
        protected void onPreExecute() {
            widget.startImporting(poiCount);
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            widget.updateImport(values[0]);
        }

        StartService(FloatingWidget widget, POICollection collection, FloatingWarnerService service)
        {
            this.widget = widget;
            this.collection = collection;
            this.poiCount = service.poiCount;
            this.service = new WeakReference<>(service);
        }
    }


    public class BwareLocationListener implements LocationListener
    {
        FloatingWidget widgetContainer;
        POICollection  collection;
        POIInfo        lastPOI;

        public String toGPXTrackPoint(Location loc) {
            byte timebytes[] = new Timestamp(loc.getTime()).toString().getBytes();
            timebytes[10]='T'; timebytes[19]='Z';

            return String.format(Locale.ROOT, "<trkpt lon=\"%f\" lat=\"%f\"><ele>%f</ele><magvar>%d</magvar><time>%s</time></trkpt>\n", loc.getLongitude(), loc.getLatitude(), loc.getAltitude(), Math.round(loc.getBearing()), new String(timebytes).substring(0,20));
        }

        @Override
        public void onLocationChanged(Location location) {
            if (location == null) return;


            BaseCoord loc = new BaseCoord(location);
            POIInfo poi = collection.getClosestPoint(loc);
            double dist = poi.distanceTo(loc);

            if (logToFile != null) {
                try {
                    if (poi != null && dist <= 300 && !trackOpened) // Here, we don't follow the set distance to avoid too verbose information
                    {
                        logToFile.append(String.format("<trk><desc>%s</desc><trkseg>\n", poi.getInfo()));
                        trackOpened = true;
                    } else if (dist > 300 && trackOpened) {
                        logToFile.append("</trkseg></trk>\n");
                        trackOpened = false;
                    }

                    if (trackOpened)
                        logToFile.append(toGPXTrackPoint(location));

                } catch (IOException e) {
                    Log.e("Bware", "Exception while storing new point in GPX: " + e.getMessage());
                    logToFile = null;
                }
            }
            widgetContainer.setClosestPOI(poi, loc, loc.speed() * 3.6f, dist);
//            lastPOI = poi;
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.v("Bware", "Provider status changed: " + s + "(" + i + ")");

        }

        @Override
        public void onProviderEnabled(String s) {
            Log.v("Bware", "Provider enabled: " + s);

        }

        @Override
        public void onProviderDisabled(String s) {
            Log.v("Bware", "Provider disabled: " + s);
        }

        BwareLocationListener(POICollection collection, FloatingWidget widgetContainer)
        {
            this.collection = collection;
            this.widgetContainer = widgetContainer;
        }
    }

}
