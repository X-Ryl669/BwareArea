package fr.byped.bwarearea;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.eatthepath.jvptree.*;

import java.util.List;

public class POICollection
{
    private POIStorageDBHelper dbHelper;
    private SQLiteDatabase database;
    final VPTree<Coordinate, POIInfo> vpTree;


    public final static String POI_TABLE = "POI"; // name of table

    /** Construct a collection
     *
     * @param context
     */
    public POICollection(Context context)
    {
        dbHelper = new POIStorageDBHelper(context);
        database = dbHelper.getWritableDatabase();
        vpTree = new VPTree<Coordinate, POIInfo>(new POIFastDistanceComputation());


    }

    public void buildVPTree()
    {
        vpTree.clear();
        Cursor c = selectRecords();
        if (c != null)
        {
            // Read all record and store them in the VP tree (not very efficient... anyway let's start by something that work)
            while (!c.isAfterLast())
            {
                POIInfo poi = new POIInfo(c.getDouble(0), c.getDouble(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getString(5));
                vpTree.add(poi);
                c.moveToNext();
            }
            c.close();
        }
    }

    private Cursor tmpRecords;
    public void buildVPTreeIteratively(int perStep)
    {
        if (tmpRecords == null)
        {
            vpTree.clear();
            tmpRecords = selectRecords();
        }
        if (tmpRecords != null)
        {
            while (!tmpRecords.isAfterLast() && perStep > 0)
            {
                POIInfo poi = new POIInfo(tmpRecords.getDouble(0), tmpRecords.getDouble(1), tmpRecords.getInt(2), tmpRecords.getInt(3), tmpRecords.getInt(4), tmpRecords.getString(5));
                vpTree.add(poi);
                tmpRecords.moveToNext();
                perStep--;
            }
            if (perStep != 0) tmpRecords.close();
        }
    }

    public long endChanges(boolean succeeded)
    {
        dbHelper.commitChanges(database, succeeded);
        if (succeeded)
            return dbHelper.getPOICount(database);
        return 0;
    }

    public void resetDB()
    {
        dbHelper.resetTables(database);
    }


    public long createRecord(POIInfo poi)
    {
        ContentValues values = new ContentValues();
        values.put("lon", poi.getLongitude());
        values.put("lat", poi.getLatitude());
        values.put("type", poi.type);
        values.put("speed", poi.speedKmh);
        values.put("dir", poi.directionDegree);
        values.put("desc", poi.description != null ? poi.description : "");
        return database.insert(POI_TABLE, null, values);
    }

    public Cursor selectRecords() {
        String[] cols = new String[]{ "lon", "lat", "type", "speed", "dir", "desc"};
        Cursor mCursor = database.query(false, POI_TABLE, cols, null
                , null, null, null, null, null);
        if (mCursor != null)
            mCursor.moveToFirst();

        return mCursor; // iterate to get each value.
    }

    // Find the closest point from the current coordinate
    public POIInfo getClosestPoint(Coordinate c)
    {
        List<POIInfo> list = vpTree.getNearestNeighbors(c, 1);
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    public long size() { return vpTree.size(); }

}
