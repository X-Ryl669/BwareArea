package fr.byped.bwarearea;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class GPXParser {
    public static class POI
    {
        double lat;
        double lon;
        String name;
    }

    /** This is the collection we are feeding with */
    POICollection collection;
    InputStream   is;
    NodeList      waypoints;
    public long lastPOICount;


    public GPXParser(POICollection collection, InputStream is)
    {
        this.collection = collection; this.is = is;
    }

    public static int typesToID[] = { R.string.unknown, R.string.static_cam, R.string.type_2, R.string.type_3, R.string.range_start, R.string.range_stop, R.string.fire_cam };

    public boolean importFromPOI(int startOffset, int qty)
    {
        try {
            if (startOffset < 1) { this.collection.resetDB(); lastPOICount = 0; }

            for (int i = startOffset; i < Math.min(startOffset+qty, waypoints.getLength()); i++)
            {
                Element wp = (Element)waypoints.item(i);
                String name = wp.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "name").item(0).getTextContent(); //xPath.evaluate("gpx:name", wp);
                // Try to find speed in the given name
                String speedName = name.replaceAll("[^0-9]*", "");

                int speed = speedName.isEmpty() ? 0 : Integer.parseInt(speedName);
                int type = (speed != 0) ? 1 : 0;


                POIInfo poi = new POIInfo(Double.parseDouble(wp.getAttribute("lon")), Double.parseDouble(wp.getAttribute("lat")), type, speed, -1);
                this.collection.createRecord(poi);
            }

            if (startOffset + qty >= waypoints.getLength())
                lastPOICount = this.collection.endChanges(true);
        } catch(Exception e)
        {
            Log.e("GPXParser", "Got exception: " + e.getMessage() + " with stack: " + e.getStackTrace());
            this.collection.endChanges(false);
            return false;
        }
        return true;
    }

    public int getPOIFromStream(Context context)
    {
        /* This code works, but it's slow as hell
        XPathFactory factory = XPathFactory.newInstance();
        XPath xPath = factory.newXPath();
        xPath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if (prefix == null) throw new NullPointerException("Null prefix");
                else if ("gpx".equals(prefix)) return "http://www.topografix.com/GPX/1/1";
                else if ("xml".equals(prefix)) return XMLConstants.XML_NS_URI;
                return XMLConstants.NULL_NS_URI;
            }

            // This method isn't necessary for XPath processing.
            public String getPrefix(String uri) {
                throw new UnsupportedOperationException();
            }

            // This method isn't necessary for XPath processing either.
            public Iterator getPrefixes(String uri) {
                throw new UnsupportedOperationException();
            }
        });
        try
        {
            waypoints = (NodeList) xPath.evaluate("/gpx:gpx/gpx:wpt", new InputSource(is), XPathConstants.NODESET);
            return waypoints.getLength();
        } catch(XPathExpressionException e)
        {
            Log.e("GPXParser", "Got exception: " + e.getMessage() + " with stack: " + e.getStackTrace());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            return 0;
        }
        */
        // So try the basic, non-XPath version
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(is)); // Seems like a 2MB XML takes 14s to parse on my emulator. I've no idea if it's too big or not.

            waypoints = doc.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "wpt");
            return waypoints.getLength();
        } catch (Exception e)
        {
            Log.e("GPXParser", "Got exception: " + e.getMessage() + " with stack: " + e.getStackTrace());
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            return 0;
        }

    }
}
