package fr.byped.bwarearea;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class POIStorageDBHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "POI";
    boolean inTransaction = false;

    private static final int DATABASE_VERSION = 2; // Force upgrading on first creation

    // Database creation sql statement
    private static final String DATABASE_CREATE = "create table POI(id integer primary key autoincrement, lon real, lat real, type integer, speed integer default 0, dir integer default -1, description text default '');";

    public POIStorageDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Method is called during creation of the database
    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    // Method is called during an upgrade of the database,
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion){
        Log.w(POIStorageDBHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        database.execSQL("DROP TABLE IF EXISTS POI;");
        onCreate(database);
    }

    /** Reset the main table */
    public void resetTables(SQLiteDatabase database)
    {
        database.execSQL("BEGIN TRANSACTION;"); inTransaction = true;
        database.execSQL("DELETE FROM POI;");
    }

    public void commitChanges(SQLiteDatabase database, boolean succeeded)
    {
        if (inTransaction) database.execSQL(succeeded ? "COMMIT;" : "ROLLBACK;");
        inTransaction = false;
    }

    public long getPOICount(SQLiteDatabase database)
    {
        Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM POI;", null);
        if (cursor == null) return 0;
        cursor.moveToFirst();
        int res = cursor.getCount() > 0 && cursor.getColumnCount() > 0 ? cursor.getInt(0) : 0;
        cursor.close();
        return res;
    }
}
