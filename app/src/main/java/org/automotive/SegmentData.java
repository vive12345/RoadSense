package org.automotive;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents data for a detected road segment (straight or curve)
 * and stores various sensor measurements collected during that segment.
 * It implements all of the required data extraction for Project 2 Part 2.
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

    // Acceleration information
    private double maxLongAcceleration; // m/s² (for straight segments)
    private double minLongAcceleration; // m/s² (for straight segments)
    private double maxLatAcceleration; // m/s² (for curve segments)
    private double minLatAcceleration; // m/s² (for curve segments)

    // Additional curve information
    private String curveDirection; // "left" or "right"
    private double curveDegrees; // total degrees of heading change
    private double averageYawRate; // °/s
    private double maxYawRate; // °/s

    // Data collection for calculations
    private List<Double> speedValues = new ArrayList<>();
    private List<Double> longAccelerationValues = new ArrayList<>();
    private List<Double> latAccelerationValues = new ArrayList<>();
    private List<Double> yawRateValues = new ArrayList<>();
    private List<Double> headingValues = new ArrayList<>();
    private List<GPScoordinates> gpsPoints = new ArrayList<>();

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

        // Add initial data points
        this.gpsPoints.add(startCoords);
        this.headingValues.add(initialHeading);

        // Initialize with extreme values to ensure they'll be updated
        this.maxSpeed = Double.MIN_VALUE;
        this.minSpeed = Double.MAX_VALUE;
        this.maxLongAcceleration = Double.MIN_VALUE;
        this.minLongAcceleration = Double.MAX_VALUE;
        this.maxLatAcceleration = Double.MIN_VALUE;
        this.minLatAcceleration = Double.MAX_VALUE;
        this.maxYawRate = Double.MIN_VALUE;
    }

    /**
     * Add a GPS coordinate to the segment
     * 
     * @param coords The GPS coordinates
     */
    public void addGPSCoordinate(GPScoordinates coords) {
        if (coords != null) {
            gpsPoints.add(coords);
        }
    }

    /**
     * Add a heading value to the segment
     * 
     * @param heading The vehicle heading in degrees
     */
    public void addHeading(double heading) {
        headingValues.add(heading);
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
     * Add longitudinal acceleration value to the segment data
     * 
     * @param acceleration The vehicle longitudinal acceleration in m/s²
     */
    public void addLongitudinalAcceleration(double acceleration) {
        longAccelerationValues.add(acceleration);

        // Update min and max values
        if (acceleration > maxLongAcceleration) {
            maxLongAcceleration = acceleration;
        }
        if (acceleration < minLongAcceleration) {
            minLongAcceleration = acceleration;
        }
    }

    /**
     * Add lateral acceleration value to the segment data
     * 
     * @param acceleration The vehicle lateral acceleration in m/s²
     */
    public void addLateralAcceleration(double acceleration) {
        latAccelerationValues.add(acceleration);

        // Update min and max values
        if (acceleration > maxLatAcceleration) {
            maxLatAcceleration = acceleration;
        }
        if (acceleration < minLatAcceleration) {
            minLatAcceleration = acceleration;
        }
    }

    /**
     * Add yaw rate value to the segment data
     * 
     * @param yawRate The vehicle yaw rate in °/s
     */
    public void addYawRateValue(double yawRate) {
        yawRateValues.add(yawRate);

        // Update max value (use absolute value for max)
        if (Math.abs(yawRate) > maxYawRate) {
            maxYawRate = Math.abs(yawRate);
        }

        // Determine curve direction based on yaw rate sign
        // For consistency, use the predominant direction during the curve
        if (type == SegmentDetector.SegmentType.CURVE) {
            // Count positive and negative yaw rates to determine predominant direction
            int positiveCount = 0;
            int negativeCount = 0;

            for (Double rate : yawRateValues) {
                if (rate > 0)
                    positiveCount++;
                else if (rate < 0)
                    negativeCount++;
            }

            // Set direction based on predominant sign
            curveDirection = (positiveCount >= negativeCount) ? "right" : "left";
        }
    }

    /**
     * Calculate all aggregate values when the segment ends
     * 
     * @param endTime      The end time of the segment in milliseconds
     * @param endCoords    The GPS coordinates at segment end
     * @param finalHeading The final vehicle heading at segment end
     */
    // Modify finalizeSegment() in SegmentData.java
    public void finalizeSegment(double endTime, GPScoordinates endCoords, double finalHeading) {
        this.endTime = endTime;
        this.endCoordinates = endCoords;

        // Add final GPS and heading
        if (endCoords != null) {
            gpsPoints.add(endCoords);
        }
        if (headingValues.size() > 0) {
            headingValues.add(finalHeading);
        }

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
        }

        // Calculate curve degrees (total heading change)
        calculateCurveDegrees();

        // If heading-based calculation fails, use yaw rate method as backup
        if (type == SegmentDetector.SegmentType.CURVE && curveDegrees < 0.1) {
            calculateCurveDegreesFromYawRate();
        }

        // Calculate segment length
        calculateSegmentLength();
    }

    /**
     * Calculate curve degrees based on heading changes
     */
    private void calculateCurveDegrees() {
        if (type == SegmentDetector.SegmentType.CURVE && headingValues.size() >= 2) {
            // Get the initial and final headings
            double initialHeading = headingValues.get(0);
            double finalHeading = headingValues.get(headingValues.size() - 1);

            // Calculate the absolute difference in headings
            double rawDifference = Math.abs(finalHeading - initialHeading);

            // Normalize to get the smaller angle (heading is 0-360)
            curveDegrees = (rawDifference > 180) ? 360 - rawDifference : rawDifference;

            // If we have more than 2 heading values, calculate total accumulated heading
            // change
            if (headingValues.size() > 2) {
                double accumulatedChange = 0;
                for (int i = 1; i < headingValues.size(); i++) {
                    double prev = headingValues.get(i - 1);
                    double curr = headingValues.get(i);
                    double diff = Math.abs(curr - prev);

                    // Normalize difference
                    if (diff > 180) {
                        diff = 360 - diff;
                    }

                    accumulatedChange += diff;
                }

                // Use accumulated change if it's larger (for curves that go back and forth)
                if (accumulatedChange > curveDegrees) {
                    curveDegrees = accumulatedChange;
                }
            }
        } else {
            curveDegrees = 0.0; // Default for straight segments or insufficient data
        }
    }

    // Enhanced length calculation for straight segments
    private void calculateSegmentLength() {
        if (type == SegmentDetector.SegmentType.STRAIGHT) {
            if (gpsPoints.size() >= 2) {
                // If we have multiple GPS points, calculate the sum of all segments
                if (gpsPoints.size() > 2) {
                    double totalLength = 0.0;
                    for (int i = 1; i < gpsPoints.size(); i++) {
                        totalLength += calculateDistance(gpsPoints.get(i - 1), gpsPoints.get(i));
                    }
                    length = totalLength;
                } else {
                    // Just start and end points
                    length = calculateDistance(startCoordinates, endCoordinates);
                }
            } else {
                length = 0.0;
            }
        } else {
            // For curve segments
            length = 0.0;
        }
    }

    // Add to SegmentData.java
    private void calculateCurveDegreesFromYawRate() {
        if (type == SegmentDetector.SegmentType.CURVE && !yawRateValues.isEmpty()) {
            // Calculate duration in seconds
            double durationSeconds = (endTime - startTime) / 1000.0;

            // If we have average yaw rate and duration, we can calculate the total rotation
            if (averageYawRate != 0 && durationSeconds > 0) {
                // Total degrees = average yaw rate * time
                double totalDegrees = Math.abs(averageYawRate) * durationSeconds;

                // Use this if our heading-based calculation failed
                if (curveDegrees <= 0.1) { // If very small or zero
                    curveDegrees = totalDegrees;
                }
            }
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
            System.out.println("  Max longitudinal acceleration: " + df.format(maxLongAcceleration) + " m/s²");
            System.out.println("  Min longitudinal acceleration: " + df.format(minLongAcceleration) + " m/s²");
        } else {
            System.out.println("\nCURVE SEGMENT DATA:");
            System.out.println("  Direction: " + curveDirection);
            System.out.println("  Curve degrees: " + df.format(curveDegrees) + "°");
            System.out.println("  Average yaw rate: " + df.format(averageYawRate) + " °/s");
            System.out.println("  Maximum yaw rate: " + df.format(maxYawRate) + " °/s");
            System.out.println("  Maximum lateral acceleration: " + df.format(maxLatAcceleration) + " m/s²");
            System.out.println("  Minimum lateral acceleration: " + df.format(minLatAcceleration) + " m/s²");
        }

        System.out.println("====================================");
    }

    // Getters and setters
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

    public double getMaxLongAcceleration() {
        return maxLongAcceleration;
    }

    public double getMinLongAcceleration() {
        return minLongAcceleration;
    }

    public double getMaxLatAcceleration() {
        return maxLatAcceleration;
    }

    public double getMinLatAcceleration() {
        return minLatAcceleration;
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