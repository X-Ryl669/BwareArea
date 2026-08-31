package fr.byped.bwarearea;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.location.Criteria;
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

    private static boolean serviceRunning = false;

    public static boolean isRunning() {
        return serviceRunning;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Create the notification channel required by Android 8.0+ (Oreo)
     * and displays the notification required for the foreground service.
     */
    private void showLocationNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "main",
                    "BwareArea Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent intent = new Intent("finish_service");
        intent.setClass(this, FloatingWarnerService.class);
        intent.putExtra("message", "From service!");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        // MANDATORY NOTIFICATION FOR FOREGROUND SERVICE
        Notification notification = new NotificationCompat.Builder(this, "main")
                .setContentTitle(getString(R.string.bware_is_running))
                .setContentText(getString(R.string.tap_to_settings))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(PendingIntent.getService(this, 0, intent, flags))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // VERY IMPORTANT: startForeground must be called within 5 seconds
        // from the time the service starts, to prevent Android from killing it
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "finish_service".equals(intent.getAction())) {
            stopCleanly();
            // Restart the process if necessary
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        return Service.START_STICKY;
    }

    @SuppressLint("all")
    private int getOverlayType() {
        // Compatibility with Older Versions of Android
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O 
                ? WindowManager.LayoutParams.TYPE_PHONE 
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();
        serviceRunning = true;
        binder = new Binder();

        // CRITICAL CALL: Call immediately after onStartCommand/onCreate
        showLocationNotification();

        // Initialize components
        collection = new POICollection(this);
        pref = getSharedPreferences("settings", Context.MODE_PRIVATE);
        poiCount = (int) pref.getLong("poiCount", 0);
        trackOpened = false;

        setTheme(R.style.AppTheme);

        mOverlayView = LayoutInflater.from(this)
                .inflate(R.layout.floating_warner_widget, null);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Initial position of the widget
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mOverlayView, params);

        widgetContainer = mOverlayView.findViewById(R.id.widgetContainer);
        widgetContainer.bindAll(mOverlayView);
        widgetContainer.setRangeAndAlertAndWarnDistance(
                pref.getBoolean("onlyRange", false),
                pref.getInt("distance", 300),
                pref.getInt("overspeed", 5)
        );

        // Touch listener to move the widget
        widgetContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            /** This is the basic double tap to zoom/dezoom function implementation */
            class GestureListener extends GestureDetector.SimpleOnGestureListener {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    Intent intent = new Intent(FloatingWarnerService.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    return true;
                }
            }

            private GestureDetector gestureDetector =
                    new GestureDetector(FloatingWarnerService.this, new GestureListener());

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

                    case MotionEvent.ACTION_MOVE:
                        float Xdiff = Math.round(event.getRawX() - initialTouchX);
                        float Ydiff = Math.round(event.getRawY() - initialTouchY);
                        params.x = initialX + (int) Xdiff;
                        params.y = initialY + (int) Ydiff;
                        mWindowManager.updateViewLayout(mOverlayView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Optional: Place it near the nearest edge
                        return true;
                }
                return false;
            }
        });

        // Start building the VPTree in the background
        new StartService(widgetContainer, collection, this).execute(poiCount);

        // Notifies the MainActivity that the service is started
        Intent broadcastIntent = new Intent("finish_activity");
        broadcastIntent.putExtra("message", "From service!");
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);

        // Prepare GPX log files if required
        if (pref.getBoolean("logFile", false)) {
            try {
                File sdFolder = new File(Environment.getExternalStorageDirectory(), "Bware");
                if (!sdFolder.exists()) sdFolder.mkdir();
                logToFile = new FileWriter(new File(sdFolder, 
                        String.format("track_%d.gpx", Calendar.getInstance().getTime().getTime())));
                logToFile.write("<?xml version='1.0' encoding='Utf-8' standalone='yes' ?>\n" +
                        "<gpx xmlns=\"http://www.topografix.com/GPX/1/0\" version=\"1.0\" creator=\"fr.byped.bwarearea\">\n");
            } catch (Exception e) {
                Log.e("Bware", "Exception while creating writer: " + e.getMessage());
                logToFile = null;
            }
        }
    }

    @Override
    public void onDestroy() {
        serviceRunning = false;
        stopLocation();
        super.onDestroy();
        if (mOverlayView != null && mWindowManager != null) {
            mWindowManager.removeView(mOverlayView);
        }
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
            } catch (IOException e) {
                Log.e("Bware", "Error writing GPX footer: " + e.getMessage());
            }
            logToFile = null;
        }
    }

    private void stopCleanly() {
        stopLocation();
        stopForeground(true);
        stopSelf();
    }

    private void doneImporting() {
        showLocationNotification();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || locationManager.getAllProviders().isEmpty()) {
            Toast.makeText(this, R.string.cant_get_location_manager, Toast.LENGTH_LONG).show();
            stopCleanly();
            return;
        }
        try {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(false);
            criteria.setBearingRequired(false);
            criteria.setCostAllowed(true);
            criteria.setPowerRequirement(Criteria.POWER_LOW);

            String provider = locationManager.getBestProvider(criteria, true);
            Log.i("Bware", "Using provider: " + provider);
            
            locListener = new BwareLocationListener(collection, widgetContainer);
            
            // Get current location
            Location loc = locationManager.getLastKnownLocation(provider);
            Log.i("Bware", "Initial location: " + loc);
            
            // Request Location Updates
            // Interval: 2000 ms, minimum distance: 50 m
            locationManager.requestLocationUpdates(provider, 2000, 50, locListener);
        } catch (SecurityException e) {
            Log.e("Bware", "Security exception: " + e.getMessage());
            Toast.makeText(this, R.string.cant_get_location_manager, Toast.LENGTH_LONG).show();
            stopCleanly();
        }
    }

    public class Binder extends android.os.Binder {
        public FloatingWarnerService getService() {
            return FloatingWarnerService.this;
        }
    }
    
    public class BaseCoord implements Coordinate {
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

    private static class StartService extends AsyncTask<Integer, Integer, String> {
        FloatingWidget widget;
        POICollection collection;
        private WeakReference<FloatingWarnerService> service;
        int poiCount;

        @Override
        protected String doInBackground(Integer... params) {
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
            if (service.get() != null) {
                service.get().doneImporting();
            }
        }

        @Override
        protected void onPreExecute() {
            widget.startImporting(poiCount);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            widget.updateImport(values[0]);
        }

        StartService(FloatingWidget widget, POICollection collection, FloatingWarnerService service) {
            this.widget = widget;
            this.collection = collection;
            this.poiCount = service.poiCount;
            this.service = new WeakReference<>(service);
        }
    }

    public class BwareLocationListener implements LocationListener {
        FloatingWidget widgetContainer;
        POICollection collection;
        POIInfo lastPOI;

        public String toGPXTrackPoint(Location loc) {
            byte timebytes[] = new Timestamp(loc.getTime()).toString().getBytes();
            timebytes[10] = 'T';
            timebytes[19] = 'Z';

            return String.format(Locale.ROOT,
                    "<trkpt lon=\"%f\" lat=\"%f\"><ele>%f</ele><magvar>%d</magvar><time>%s</time></trkpt>\n",
                    loc.getLongitude(), loc.getLatitude(), loc.getAltitude(),
                    Math.round(loc.getBearing()), new String(timebytes).substring(0, 20));
        }

        @Override
        public void onLocationChanged(Location location) {
            if (location == null) return;

            BaseCoord loc = new BaseCoord(location);
            POIInfo poi = collection.getClosestPoint(loc);
            if (poi == null) return;
            double dist = poi.distanceTo(loc);

            if (logToFile != null) {
                try {
                    if (poi != null && dist <= 300 && !trackOpened) {
                        logToFile.append(String.format("<trk><desc>%s</desc><trkseg>\n", poi.getInfo()));
                        trackOpened = true;
                    } else if (dist > 300 && trackOpened) {
                        logToFile.append("</trkseg></trk>\n");
                        trackOpened = false;
                    }

                    if (trackOpened) {
                        logToFile.append(toGPXTrackPoint(location));
                    }
                } catch (IOException e) {
                    Log.e("Bware", "Exception storing GPX: " + e.getMessage());
                    logToFile = null;
                }
            }

            widgetContainer.setClosestPOI(poi, loc, loc.getSpeed() * 3.6f, dist);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.v("Bware", "Provider status changed: " + provider + "(" + status + ")");
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.v("Bware", "Provider enabled: " + provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.v("Bware", "Provider disabled: " + provider);
        }

        BwareLocationListener(POICollection collection, FloatingWidget widgetContainer) {
            this.collection = collection;
            this.widgetContainer = widgetContainer;
        }
    }
}
