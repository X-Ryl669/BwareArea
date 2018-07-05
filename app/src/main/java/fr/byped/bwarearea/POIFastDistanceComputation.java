package fr.byped.bwarearea;

import com.eatthepath.jvptree.*;

public class POIFastDistanceComputation implements DistanceFunction<Coordinate> {

    public double getDistance(final Coordinate firstPoint, final Coordinate secondPoint) {
        final double deltaX = firstPoint.getLatitude() - secondPoint.getLatitude();
        final double deltaY = firstPoint.getLongitude() - secondPoint.getLongitude();

        return Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
    }
}