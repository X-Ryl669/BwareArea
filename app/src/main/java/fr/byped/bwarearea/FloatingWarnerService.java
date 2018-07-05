package fr.byped.bwarearea;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.constraint.solver.widgets.WidgetContainer;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

public class FloatingWarnerService extends Service {
    private WindowManager mWindowManager;
    private View mOverlayView;
    private FloatingWidget widgetContainer;
    private POICollection collection;
    private SharedPreferences pref;
    private Binder binder;
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
                    stopSelf();
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
        new StartService(widgetContainer, collection).execute(poiCount);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOverlayView != null)
            mWindowManager.removeView(mOverlayView);
    }


    public FloatingWarnerService() {
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
            widget.doneImporting();

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
