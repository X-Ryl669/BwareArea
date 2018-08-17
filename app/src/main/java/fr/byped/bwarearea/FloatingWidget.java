package fr.byped.bwarearea;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;

public class FloatingWidget extends FrameLayout
{
    TextView speed;
    TextView type;
    TextView distance;
    TextView currentSpeed;
    ImageView dontWorry;
    AnimationDrawable overspeedAnim;
    Drawable  defaultBackground;
    ProgressBar startProgress;
    MediaPlayer player;
    boolean  onlyRange;
    int      warnDistance;
    int      alertOverspeed;
    boolean  silentAlert;

    double   distanceAvg;
    double   lastDistanceAvg;


    POIInfo lastPOI;
    OnTouchListener touchListener;


    void bindAll(View view)
    {
        speed = view.findViewById(R.id.maxSpeed);
        type = view.findViewById(R.id.poiType);
        distance = view.findViewById(R.id.distance);
        startProgress = view.findViewById(R.id.startProgress);
        dontWorry = view.findViewById(R.id.allOk);
        currentSpeed = view.findViewById(R.id.currentSpeed);
        overspeedAnim = (AnimationDrawable)ContextCompat.getDrawable(view.getContext(), R.drawable.speed_background_animation);
        defaultBackground = ContextCompat.getDrawable(view.getContext(), R.drawable.speed_background_normal);
        player = MediaPlayer.create(view.getContext(), R.raw.coin_fall_cc0);
/*        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                player.start();
            }
        });*/
        touchListener = null;
    }

    public void setRangeAndAlertAndWarnDistance(boolean onlyRange, int warnDistance, int alertOverspeed)
    {
        this.onlyRange = onlyRange;
        this.warnDistance = warnDistance;
        this.alertOverspeed = alertOverspeed;
    }

    public FloatingWidget(@NonNull Context context) {
        super(context);
    }

    public FloatingWidget(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatingWidget(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnTouchListener(OnTouchListener listener) { touchListener = listener; }

    public String getPOITypeAsString(int type)
    {
        if (type < POIInfo.typesToID.length)
            return getContext().getString(POIInfo.typesToID[type]);
        return getContext().getString(POIInfo.typesToID[0]);
    }

    private void dontWarn(float speedInKmH)
    {
        distance.setVisibility(View.GONE);
        type.setVisibility(View.GONE);
        speed.setVisibility(View.GONE);
        dontWorry.setVisibility(View.VISIBLE);
    }

    private void warn(double dist, float speedInKmH)
    {
        if (onlyRange) distance.setText("---");
        else distance.setText(String.format("%dm", Math.round(dist)));
        distance.setVisibility(View.VISIBLE);
        type.setVisibility(View.VISIBLE);
        speed.setVisibility(View.VISIBLE);
        dontWorry.setVisibility(View.GONE);

        if (alertOverspeed > 0 && Math.round(speedInKmH) > (lastPOI.speedKmh + alertOverspeed) && !silentAlert)
        {   // Alert should be raised, only if not silenced
            speed.setBackground(overspeedAnim);
            if (!overspeedAnim.isRunning()) {
                overspeedAnim.start();
                player.start();
            }
        }
        else
        {
            speed.setBackground(defaultBackground);
            overspeedAnim.stop();
            player.stop();
            try {
                player.prepare();
            } catch(IOException e)
            {
                Log.e("Bware", "Error while preparing the player " + e.getMessage());
            }
        }
    }

    public void setClosestPOI(POIInfo poi, Coordinate current, float speedInKmH, double dist)
    {
        if (poi == null) return;

        // Should we make the next alert silent ?
        // We don't if it's a new POI
        if (lastPOI == null || lastPOI.id != poi.id) {
            silentAlert = false;
            // Reset when changing POI
            distanceAvg = dist;
            lastDistanceAvg = dist;
            lastPOI = poi;
        }

        // We do however if we are leaving the POI
        // Leaving is defined as the windowed distance average increasing
        double alpha = 0.2;
        distanceAvg = distanceAvg * alpha + (1 - alpha) * dist;
        // Get the minimum distance now
        if (lastDistanceAvg > distanceAvg) lastDistanceAvg = distanceAvg;
        // Check if we need to silent an alert (we have a 10% margin)
        if (distanceAvg > lastDistanceAvg * 1.1)
            silentAlert = true;


        // Remember the value
        if (poi.speedKmh != 0)
            speed.setText(String.format("%d", poi.speedKmh));
        else
            speed.setText("???");
        type.setText(getPOITypeAsString(poi.type));
        currentSpeed.setText(String.format("%dkm/h", Math.round(speedInKmH)));

        if (dist > warnDistance)
            dontWarn(speedInKmH);
        else
            warn(dist, speedInKmH);
    }

    private int poiCount;
    public void startImporting(int max)
    {
        poiCount = max;
        startProgress.setMax(max);
        startProgress.setProgress(0);
        speed.setVisibility(View.GONE);
        startProgress.setVisibility(View.VISIBLE);
        type.setText(R.string.indexing);

    }

    public void doneImporting()
    {
        startProgress.setVisibility(View.GONE);
        speed.setVisibility(View.VISIBLE);
        type.setText(R.string.done);
        distance.setText("");

    }

    public void updateImport(int value)
    {
        startProgress.setProgress(value);
        distance.setText(String.format("%d%%", poiCount != 0 ? value * 100 / poiCount : 100));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (touchListener != null)
            return touchListener.onTouch(this, event);
        return super.onTouchEvent(event);
    }
}
