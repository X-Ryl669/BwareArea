package fr.byped.bwarearea;



/** The Point of Interest information */
public class POIInfo implements Coordinate
{
    private double longitude;
    private double latitude;
    /** The point of interest unique identifier */
    public int id;
    /** The point of interest type */
    public int type;
    /** The point of interest speed */
    public int speedKmh;
    /** The point of interest direction (or < 0 if none known) */
    public int directionDegree;
    /** A description for this text */
    public String description;

    public static int typesToID[] = { R.string.unknown, R.string.static_cam, R.string.type_2, R.string.type_3, R.string.range_start, R.string.range_stop, R.string.fire_cam };


    public POIInfo(double longitude, double latitude, int type, int speed, int dir)
    {
        this.setLongitude(longitude); this.setLatitude(latitude);
        this.type = type;
        speedKmh = speed;
        directionDegree = dir;
    }

    public POIInfo(double longitude, double latitude, int type, int speed, int dir, int id)
    {
        this.setLongitude(longitude); this.setLatitude(latitude);
        this.type = type;
        this.id = id;
        speedKmh = speed;
        directionDegree = dir;
    }


    public POIInfo(double longitude, double latitude, int type, int speed, int dir, String description)
    {
        this(longitude, latitude, type, speed, dir);
        this.description = description;
    }

    public POIInfo(double longitude, double latitude, int type, int speed, int dir, String description, int id)
    {
        this(longitude, latitude, type, speed, dir, id);
        this.description = description;
    }

    public final static double AVERAGE_RADIUS_OF_EARTH_M = 6371000;
    /** Compute distance to another point in meter using Haversine formula */
    public double distanceTo(double longitude, double latitude)
    {
        double latDistance = Math.toRadians(this.getLatitude() - latitude);
        double lngDistance = Math.toRadians(this.getLongitude() - longitude);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(this.getLatitude())) * Math.cos(Math.toRadians(latitude))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return (int) (Math.round(AVERAGE_RADIUS_OF_EARTH_M * c));
    }

    public double distanceTo(Coordinate c) { return distanceTo(c.getLongitude(), c.getLatitude()); }

    public String getInfo()
    {
        return String.format("id:%d, lat:%f, lon:%f, type:%d, speed:%d, dir: %d", id, latitude, longitude, type, speedKmh, directionDegree);
    }

    /** The Point of interest location */
    @Override
    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
}

