package fr.byped.bwarearea;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class FloatingWidget extends FrameLayout
{
    TextView speed;
    TextView type;
    TextView distance;
    ProgressBar startProgress;

    Coordinate lastPOI;
    OnTouchListener touchListener;


    void bindAll(View view)
    {
        speed = view.findViewById(R.id.maxSpeed);
        type = view.findViewById(R.id.poiType);
        distance = view.findViewById(R.id.distance);
        startProgress = view.findViewById(R.id.startProgress);
        touchListener = null;
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

    public void setClosestPOI(POIInfo poi)
    {
        speed.setText(poi.speedKmh);
        type.setText(getPOITypeAsString(poi.type));
        lastPOI = poi;
    }

    private int poiCount;
    public void startImporting(int max)
    {
        poiCount = max;
        startProgress.setMax(max);
        startProgress.setProgress(0);
        speed.setVisibility(View.GONE);
        startProgress.setVisibility(View.VISIBLE);
        type.setText("Creating DB");
        type.setTextSize(type.getTextSize() * 0.5f);
    }

    public void doneImporting()
    {
        startProgress.setVisibility(View.GONE);
        speed.setVisibility(View.VISIBLE);
        type.setText("Done");
        type.setTextSize(type.getTextSize() / 0.5f);
    }

    public void updateImport(int value)
    {
        startProgress.setProgress(value);
        type.setText(String.format("Indexing %d%%", value * 100 / poiCount));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (touchListener != null)
            return touchListener.onTouch(this, event);
        return super.onTouchEvent(event);
    }
}
