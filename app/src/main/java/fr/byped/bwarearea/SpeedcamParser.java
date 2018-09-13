package fr.byped.bwarearea;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Parse a speedcam.txt file (iGO format) or CSV format */
public class SpeedcamParser
{
    /** This is the collection we are feeding with */
    POICollection collection;
    public long lastPOICount;


    public SpeedcamParser(POICollection collection)
    {
        this.collection = collection;
    }

    public static String[] getLinesFromStream(Context context, InputStream stream)
    {
        try {

            String fileContent = convertStreamToString(stream);
            stream.close();
            // Split the first line of the file
            String lines[] = fileContent.split("\n");
            if (lines.length < 2) throw new Exception("This file does not contain enough lines");
            String line = lines[0].replaceAll(" ", "");
            // Ensure it's the right format
            if (!line.toLowerCase().startsWith("x,y,type,speed,dirtype,direction"))
                throw new Exception("This file does not follow the expected format (check it's following iGO format)");
            return lines;
        } catch(Exception e)
        {
            Log.e("SpeedcamParser", "Got exception: " + e.getMessage() + " with stack: " + e.getStackTrace());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    /** Split the parsing of the given file in 3 steps, first extract and validate the lines from the file */
    public static String[] getLinesFromSpeedcamFile(Context context, String filePath)
    {
        try {
            FileInputStream fin = new FileInputStream(new File(filePath));
            return getLinesFromStream(context, fin);
        } catch(FileNotFoundException e)
        {
            return null;
        }
    }

    /** Now process some importation */
    public boolean importFromLines(String[] lines, int startOffset, int qty)
    {
        try {
            if (startOffset < 1) { this.collection.resetDB(); lastPOICount = 0; }

            for (int i = startOffset; i < Math.min(startOffset+qty, lines.length); i++)
            {
                String line = lines[i];
                // Ignore invalid lines
                if (!line.substring(0, 1).matches("[-0-9\\.]")) continue;
                // We need to replace any error in the file (typically, double comma, etc...)
                line = line.replaceAll(",,", ",").replaceAll(" ", "");
                // Then split in terms
                String items[] = line.split(",");
                if (items.length < 6) throw new Exception("The line " + i + " is not valid: " + line);
                boolean hasDirection = items[4] != "0";
                double direction = hasDirection ? Double.parseDouble(items[5]) : -1;
                POIInfo poi = new POIInfo(Double.parseDouble(items[0]), Double.parseDouble(items[1]), Integer.parseInt(items[2]), Integer.parseInt(items[3]), (int)direction);
                this.collection.createRecord(poi);
            }

            if (startOffset + qty >= lines.length)
                lastPOICount = this.collection.endChanges(true);
        } catch(Exception e)
        {
            Log.e("SpeedcamParser", "Got exception: " + e.getMessage() + " with stack: " + e.getStackTrace());
            this.collection.endChanges(false);
            return false;
        }
        return true;
    }

    /** The expected format is a CSV with first line being: X,Y,TYPE,SPEED,DIRTYPE,DIRECTION, then the line flows */
    public boolean parseSpeedcamFile(Context context, String filePath)
    {
        try {

            String fileContent = getStringFromFile(filePath);
            // Split the first line of the file
            String lines[] = fileContent.split("\n");
            if (lines.length < 2) throw new Exception("This file does not contain enough lines");
            String line = lines[0].replaceAll(" ", "");
            // Ensure it's the right format
            if (!line.toLowerCase().startsWith("x,y,type,speed,dirtype,direction"))
                throw new Exception("This file does not follow the expected format (check it's following iGO format)");
            // Then start importing!
            this.collection.resetDB();
            for (int i = 1; i < lines.length; i++)
            {
                line = lines[i];
                // Ignore invalid lines
                if (!line.substring(0, 1).matches("[-0-9\\.]")) continue;
                // We need to replace any error in the file (typically, double comma, etc...)
                line = line.replaceAll(",,", ",").replaceAll(" ", "");
                // Then split in terms
                String items[] = line.split(",");
                if (items.length < 6) throw new Exception("The line " + i + " is not valid: " + line);
                boolean hasDirection = items[4] != "0";
                double direction = hasDirection ? Double.parseDouble(items[5]) : -1;
                POIInfo poi = new POIInfo(Double.parseDouble(items[0]), Double.parseDouble(items[1]), Integer.parseInt(items[2]), Integer.parseInt(items[3]), (int)direction);
                this.collection.createRecord(poi);
                if ((i % 1024) == 0)
                    Toast.makeText(context, "Imported so far: " + i + " lines", Toast.LENGTH_SHORT).show();
            }

            this.collection.endChanges(true);
            Toast.makeText(context, "Imported " + collection.size() + " POI", Toast.LENGTH_SHORT).show();
        } catch(Exception e)
        {
            Log.e("SpeedcamParser", "Got exception: " + e.getMessage() + " with stack: " + e.getStackTrace());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            this.collection.endChanges(false);
            return false;
        }
        return true;
    }

    /** Helper to convert a stream to a string that can be parsed later on */
    public static String convertStreamToString(InputStream is) throws Exception
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        Boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if(firstLine) {
                sb.append(line);
                firstLine = false;
            } else {
                sb.append("\n").append(line);
            }
        }
        reader.close();
        return sb.toString();
    }

    /** Read a file to a string */
    public static String getStringFromFile (String filePath) throws Exception
    {
        return getStringFromFile(new File(filePath));
    }
    /** Read a file to a string */
    public static String getStringFromFile (File fl) throws Exception
    {
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        // Make sure we close all streams.
        fin.close();
        return ret;
    }
}
