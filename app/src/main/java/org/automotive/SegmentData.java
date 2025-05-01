package org.automotive;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents data for a detected road segment (straight or curve)
 * and stores various sensor measurements collected during that segment.
 */
public class SegmentData {
    // Segment type (straight or curve)
    private SegmentDetector.SegmentType type;

    // GPS coordinates
    private GPScoordinates startCoordinates;
    private GPScoordinates endCoordinates;

    // Time information
    private double startTime; // milliseconds
    private double endTime; // milliseconds

    // Segment length (calculated for straight segments)
    private double length; // meters

    // Vehicle speed information
    private double averageSpeed; // km/h
    private double maxSpeed; // km/h
    private double minSpeed; // km/h

    // Acceleration information (longitudinal for straight, lateral for curve)
    private double maxAcceleration; // m/s²
    private double minAcceleration; // m/s²

    // Additional curve information
    private String curveDirection; // "left" or "right"
    private double curveDegrees; // total degrees of heading change
    private double averageYawRate; // °/s
    private double maxYawRate; // °/s

    // Data collection for calculations
    private List<Double> speedValues = new ArrayList<>();
    private List<Double> accelerationValues = new ArrayList<>();
    private List<Double> yawRateValues = new ArrayList<>();
    private double initialHeading; // for calculating curve degrees

    /**
     * Constructor for a new segment
     * 
     * @param type           The segment type (STRAIGHT or CURVE)
     * @param startTime      The start time of the segment in milliseconds
     * @param startCoords    The GPS coordinates at segment start
     * @param initialHeading The initial vehicle heading at segment start
     */
    public SegmentData(SegmentDetector.SegmentType type, double startTime,
            GPScoordinates startCoords, double initialHeading) {
        this.type = type;
        this.startTime = startTime;
        this.startCoordinates = startCoords;
        this.initialHeading = initialHeading;

        // Initialize with extreme values to ensure they'll be updated
        this.maxSpeed = Double.MIN_VALUE;
        this.minSpeed = Double.MAX_VALUE;
        this.maxAcceleration = Double.MIN_VALUE;
        this.minAcceleration = Double.MAX_VALUE;
        this.maxYawRate = Double.MIN_VALUE;
    }

    /**
     * Add speed value to the segment data
     * 
     * @param speed The vehicle speed in km/h
     */
    public void addSpeedValue(double speed) {
        speedValues.add(speed);

        // Update min and max values
        if (speed > maxSpeed) {
            maxSpeed = speed;
        }
        if (speed < minSpeed) {
            minSpeed = speed;
        }
    }

    /**
     * Add acceleration value to the segment data
     * 
     * @param acceleration The vehicle acceleration (longitudinal for straight,
     *                     lateral for curve) in m/s²
     */
    public void addAccelerationValue(double acceleration) {
        accelerationValues.add(acceleration);

        // Update min and max values
        if (acceleration > maxAcceleration) {
            maxAcceleration = acceleration;
        }
        if (acceleration < minAcceleration) {
            minAcceleration = acceleration;
        }
    }

    /**
     * Add yaw rate value to the segment data
     * 
     * @param yawRate The vehicle yaw rate in °/s
     */
    public void addYawRateValue(double yawRate) {
        yawRateValues.add(yawRate);

        // Update max value
        if (Math.abs(yawRate) > maxYawRate) {
            maxYawRate = Math.abs(yawRate);
        }

        // Determine curve direction based on yaw rate sign
        if (type == SegmentDetector.SegmentType.CURVE && curveDirection == null) {
            curveDirection = (yawRate > 0) ? "right" : "left";
        }
    }

    /**
     * Calculate all aggregate values when the segment ends
     * 
     * @param endTime      The end time of the segment in milliseconds
     * @param endCoords    The GPS coordinates at segment end
     * @param finalHeading The final vehicle heading at segment end
     */
    public void finalizeSegment(double endTime, GPScoordinates endCoords, double finalHeading) {
        this.endTime = endTime;
        this.endCoordinates = endCoords;

        // Calculate average speed
        if (!speedValues.isEmpty()) {
            double sum = 0;
            for (double value : speedValues) {
                sum += value;
            }
            averageSpeed = sum / speedValues.size();
        }

        // Calculate average yaw rate for curves
        if (type == SegmentDetector.SegmentType.CURVE && !yawRateValues.isEmpty()) {
            double sum = 0;
            for (double value : yawRateValues) {
                sum += Math.abs(value); // Use absolute values for average
            }
            averageYawRate = sum / yawRateValues.size();

            // Calculate curve degrees (heading change)
            curveDegrees = Math.abs(finalHeading - initialHeading);
            if (curveDegrees > 180) {
                curveDegrees = 360 - curveDegrees; // Take the smaller angle
            }
        }

        // Calculate length for straight segments using GPS coordinates
        if (type == SegmentDetector.SegmentType.STRAIGHT) {
            length = calculateDistance(startCoordinates, endCoordinates);
        }
    }

    /**
     * Calculate the distance between two GPS coordinates using the Haversine
     * formula
     * 
     * @param start The starting coordinates
     * @param end   The ending coordinates
     * @return The distance in meters
     */
    private double calculateDistance(GPScoordinates start, GPScoordinates end) {
        final double EARTH_RADIUS = 6371000; // Earth radius in meters

        double lat1 = Math.toRadians(start.getLatitude());
        double lng1 = Math.toRadians(start.getLongitude());
        double lat2 = Math.toRadians(end.getLatitude());
        double lng2 = Math.toRadians(end.getLongitude());

        double dLat = lat2 - lat1;
        double dLng = lng2 - lng1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * Print a formatted representation of the segment data
     */
    public void print() {
        DecimalFormat df = new DecimalFormat("0.00");

        System.out.println("=========== SEGMENT DATA ===========");
        System.out.println("Type: " + type);
        System.out.println("Duration: " + df.format(endTime - startTime) + " ms");

        System.out.println("\nSTART:");
        System.out.println("  Time: " + df.format(startTime) + " ms");
        System.out.println("  GPS: " + startCoordinates.getLatitude() + ", " + startCoordinates.getLongitude());

        System.out.println("\nEND:");
        System.out.println("  Time: " + df.format(endTime) + " ms");
        System.out.println("  GPS: " + endCoordinates.getLatitude() + ", " + endCoordinates.getLongitude());

        System.out.println("\nSPEED:");
        System.out.println("  Average: " + df.format(averageSpeed) + " km/h");
        System.out.println("  Maximum: " + df.format(maxSpeed) + " km/h");
        System.out.println("  Minimum: " + df.format(minSpeed) + " km/h");

        if (type == SegmentDetector.SegmentType.STRAIGHT) {
            System.out.println("\nSTRAIGHT SEGMENT DATA:");
            System.out.println("  Length: " + df.format(length) + " m");
            System.out.println("  Max longitudinal acceleration: " + df.format(maxAcceleration) + " m/s²");
            System.out.println("  Min longitudinal acceleration: " + df.format(minAcceleration) + " m/s²");
        } else {
            System.out.println("\nCURVE SEGMENT DATA:");
            System.out.println("  Direction: " + curveDirection);
            System.out.println("  Curve degrees: " + df.format(curveDegrees) + "°");
            System.out.println("  Average yaw rate: " + df.format(averageYawRate) + " °/s");
            System.out.println("  Maximum yaw rate: " + df.format(maxYawRate) + " °/s");
            System.out.println("  Maximum lateral acceleration: " + df.format(maxAcceleration) + " m/s²");
            System.out.println("  Minimum lateral acceleration: " + df.format(minAcceleration) + " m/s²");
        }

        System.out.println("====================================");
    }

    // Getters
    public SegmentDetector.SegmentType getType() {
        return type;
    }

    public GPScoordinates getStartCoordinates() {
        return startCoordinates;
    }

    public GPScoordinates getEndCoordinates() {
        return endCoordinates;
    }

    public double getStartTime() {
        return startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public double getAverageSpeed() {
        return averageSpeed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getMinSpeed() {
        return minSpeed;
    }

    public double getMaxAcceleration() {
        return maxAcceleration;
    }

    public double getMinAcceleration() {
        return minAcceleration;
    }

    public String getCurveDirection() {
        return curveDirection;
    }

    public double getCurveDegrees() {
        return curveDegrees;
    }

    public double getAverageYawRate() {
        return averageYawRate;
    }

    public double getMaxYawRate() {
        return maxYawRate;
    }

    public double getLength() {
        return length;
    }
}