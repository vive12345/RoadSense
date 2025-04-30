package org.automotive;

import java.util.ArrayList;
import java.util.List;

public class SegmentData {
    SegmentType type;
    GPScoordinates startGPS, endGPS;
    List<Double> speeds = new ArrayList<>();
    List<Double> yawRates = new ArrayList<>();
    List<Double> longAccels = new ArrayList<>();
    List<Double> latAccels = new ArrayList<>();

    double totalDistance;  // use haversine between successive GPS points
    double startHeading, endHeading;


    private String formatGPS(GPScoordinates gps) {
        return String.format("(%.6f, %.6f)", gps.getLatitude(), gps.getLongitude());
    }

    private double getAvg(List<Double> values) {
        return values.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private double getMin(List<Double> values) {
        return values.stream().mapToDouble(d -> d).min().orElse(0.0);
    }

    private double getMax(List<Double> values) {
        return values.stream().mapToDouble(d -> d).max().orElse(0.0);
    }


    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371e3; // Earth radius in meters
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // in meters
    }

    private String getCurveDirection() {
        double delta = (endHeading - startHeading + 360) % 360;
        return delta > 180 ? "Left" : "Right";
    }


    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    public void print() {
        System.out.println("Segment Type: " + type);
        System.out.println("Start GPS: " + formatGPS(startGPS));
        System.out.println("End GPS: " + formatGPS(endGPS));
        System.out.printf("Avg Speed: %.2f km/h\n", getAvg(speeds));
        System.out.printf("Max Speed: %.2f km/h | Min Speed: %.2f km/h\n", getMax(speeds), getMin(speeds));

        if (type == SegmentType.STRAIGHT) {
            System.out.printf("Length: %.2f m | Max Accel: %.2f | Min Accel: %.2f\n",
                    totalDistance, getMax(longAccels), getMin(longAccels));
        } else {
            System.out.printf("Curve Direction: %s\n", getCurveDirection() );
            System.out.printf("Heading Change: %.2f° | Avg Yaw: %.2f | Max Yaw: %.2f\n",
                    Math.abs(endHeading - startHeading), getAvg(yawRates), getMax(yawRates));
        }
    }

}
