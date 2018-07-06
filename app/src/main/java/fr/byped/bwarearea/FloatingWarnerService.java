package fr.byped.bwarearea;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.constraint.solver.widgets.WidgetContainer;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

public class FloatingWarnerService extends Service implements LocationListener {
    private WindowManager mWindowManager;
    private View mOverlayView;
    private FloatingWidget widgetContainer;
    private POICollection collection;
    private SharedPreferences pref;
    private Binder binder;
    private LocationManager locationManager;
    private int poiCount;



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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

        setTheme(R.style.AppTheme);

        mOverlayView = LayoutInflater.from(this).inflate(R.layout.floating_warner_widget, null);


        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);


        //Specify the view position
        params.gravity = Gravity.TOP | Gravity.START;        //Initially view will be added to top-left corner
        params.x = 0;
        params.y = 100;


        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mOverlayView, params);

        widgetContainer = (FloatingWidget)mOverlayView.findViewById(R.id.widgetContainer);
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
        new StartService(widgetContainer, collection).execute(poiCount);

        Intent intent = new Intent("finish_activity");
        // You can also include some extra data.
        intent.putExtra("message", "From service!");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopLocation();
        super.onDestroy();
        if (mOverlayView != null)
            mWindowManager.removeView(mOverlayView);
    }


    public FloatingWarnerService() {
    }

    private void stopLocation() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            locationManager = null;
        }

    }

    private void stopCleanly() {
        stopLocation();
        stopSelf();
    }

    /** Location stuff below */
    private void doneImporting()
    {
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
                Log.i("Bware", "provider " + locationManager.GPS_PROVIDER);
                // search updated location
               // onLocationChanged(locationManager.getLastKnownLocation(locationManager.GPS_PROVIDER));
               // locationManager.requestLocationUpdates(locationManager.GPS_PROVIDER, 2000, 50, this);
                locationManager.requestLocationUpdates(locationManager.GPS_PROVIDER, 0, 0, this);
                locationManager.requestLocationUpdates(locationManager.NETWORK_PROVIDER, 0, 0, this);
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

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        BaseCoord loc = new BaseCoord(location);
        POIInfo poi = collection.getClosestPoint(loc);
        widgetContainer.setClosestPOI(poi, loc, loc.speed());
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

    public class Binder extends android.os.Binder {
        public FloatingWarnerService getService() {
            return FloatingWarnerService.this;
        }
    }



    class StartService extends AsyncTask<Integer, Integer, String>
    {
        FloatingWidget widget;
        POICollection   collection;

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
            FloatingWarnerService.this.doneImporting();

        }
        @Override
        protected void onPreExecute() {
            widget.startImporting(poiCount);
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            widget.updateImport(values[0]);
        }

        StartService(FloatingWidget widget, POICollection collection)
        {
            this.widget = widget;
            this.collection = collection;
        }
    }


}
