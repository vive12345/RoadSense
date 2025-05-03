package org.automotive.utils;

import org.automotive.GPScoordinates;
import java.util.List;

/**
 * Utility class for GPS-related calculations.
 */
public class GPSUtils {
    private static final double EARTH_RADIUS = 6371000;

    private GPSUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Calculate the distance between two GPS coordinates using the Haversine
     * formula
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lng1Rad = Math.toRadians(lng1);
        double lat2Rad = Math.toRadians(lat2);
        double lng2Rad = Math.toRadians(lng2);

        double dLat = lat2Rad - lat1Rad;
        double dLng = lng2Rad - lng1Rad;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Calculate the distance between two GPS coordinates
     */
    public static double calculateDistance(GPScoordinates pos1, GPScoordinates pos2) {
        return calculateDistance(
                pos1.getLatitude(), pos1.getLongitude(),
                pos2.getLatitude(), pos2.getLongitude());
    }

    /**
     * Calculate the heading between two GPS coordinates
     */
    public static double calculateHeading(GPScoordinates from, GPScoordinates to) {
        double fromLat = Math.toRadians(from.getLatitude());
        double fromLng = Math.toRadians(from.getLongitude());
        double toLat = Math.toRadians(to.getLatitude());
        double toLng = Math.toRadians(to.getLongitude());

        double dLng = toLng - fromLng;

        double y = Math.sin(dLng) * Math.cos(toLat);
        double x = Math.cos(fromLat) * Math.sin(toLat) -
                Math.sin(fromLat) * Math.cos(toLat) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));

        return (bearing + 360) % 360; // Normalize to 0-360 degrees
    }

    /**
     * Calculate the total heading change in degrees
     */
    public static double calculateTotalHeadingChange(List<Double> headings) {
        if (headings == null || headings.size() < 2) {
            return 0.0;
        }

        double totalChange = 0.0;
        for (int i = 1; i < headings.size(); i++) {
            double prev = headings.get(i - 1);
            double curr = headings.get(i);
            double diff = Math.abs(curr - prev);

            if (diff > 180) {
                diff = 360 - diff;
            }

            totalChange += diff;
        }

        return totalChange;
    }
}