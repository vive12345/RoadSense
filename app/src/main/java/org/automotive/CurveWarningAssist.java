package org.automotive;

import org.automotive.utils.GPSUtils;
import java.text.DecimalFormat;

/**
 * The CurveWarningAssist class implements an Advanced Driver Assistance System
 * (ADAS)
 * that provides warnings about upcoming road segments, especially curves.
 * It uses previously recorded segment data to inform the driver about upcoming
 * road features.
 */
public class CurveWarningAssist {
    // Distance thresholds for warnings (in meters)
    private static final double LONG_DISTANCE_THRESHOLD = 200.0; // Far warning
    private static final double MEDIUM_DISTANCE_THRESHOLD = 100.0; // Medium warning
    private static final double SHORT_DISTANCE_THRESHOLD = 50.0; // Immediate warning

    // Formatter for numerical values
    private DecimalFormat df = new DecimalFormat("0.0");

    // Current segment collection being used for detection
    private SegmentCollection segmentCollection;

    // Most recently detected upcoming segment
    private SegmentData upcomingSegment;

    // Distance to the upcoming segment
    private double distanceToUpcomingSegment;

    /**
     * Constructor to initialize the CurveWarningAssist with saved segment data
     * 
     * @param segmentCollection Collection of road segments from previous drive
     */
    public CurveWarningAssist(SegmentCollection segmentCollection) {
        this.segmentCollection = segmentCollection;
        this.upcomingSegment = null;
        this.distanceToUpcomingSegment = Double.MAX_VALUE;
    }

    /**
     * Update the ADAS with current vehicle position and simulation time
     * 
     * @param currentPosition Current GPS coordinates of the vehicle
     * @param currentTime     Current simulation time in milliseconds
     * @return A formatted warning message to display
     */
    public String update(GPScoordinates currentPosition, double currentTime) {
        if (segmentCollection == null || currentPosition == null) {
            return "ADAS: No segment data available";
        }

        // Find the nearest upcoming segment
        SegmentData newUpcomingSegment = segmentCollection.findNearestUpcomingSegment(
                currentPosition, currentTime);

        // If we found an upcoming segment
        if (newUpcomingSegment != null) {
            upcomingSegment = newUpcomingSegment;
            distanceToUpcomingSegment = GPSUtils.calculateDistance(
                    currentPosition, upcomingSegment.getStartCoordinates());

            // Generate appropriate warning based on distance and segment type
            return generateWarningMessage();
        } else {
            return "ADAS: No upcoming segments detected";
        }
    }

    /**
     * Generate a warning message based on the distance to upcoming segment
     * 
     * @return Formatted warning message
     */
    private String generateWarningMessage() {
        StringBuilder message = new StringBuilder();

        // Add basic information about upcoming segment
        message.append("Next ").append(upcomingSegment.getType()).append(": ");
        message.append(df.format(distanceToUpcomingSegment)).append("m");

        // Add curve direction if segment is a curve
        if (upcomingSegment.getType() == SegmentDetector.SegmentType.CURVE) {
            message.append(" (").append(upcomingSegment.getCurveDirection()).append(")");

            // Add severity information for curves
            if (distanceToUpcomingSegment <= SHORT_DISTANCE_THRESHOLD) {
                message.append(" [!] IMMEDIATE CURVE");
            } else if (distanceToUpcomingSegment <= MEDIUM_DISTANCE_THRESHOLD) {
                message.append(" [!] PREPARE FOR CURVE");
            }

            // Add speed recommendation for tight curves
            if (upcomingSegment.getAverageSpeed() < 30.0) {
                message.append(" - Reduce speed");
            }
        }

        return message.toString();
    }

    /**
     * Check if an upcoming segment is detected
     * 
     * @return true if an upcoming segment is detected, false otherwise
     */
    public boolean hasUpcomingSegment() {
        return upcomingSegment != null;
    }

    /**
     * Get the upcoming segment
     * 
     * @return The upcoming segment data
     */
    public SegmentData getUpcomingSegment() {
        return upcomingSegment;
    }

    /**
     * Get the distance to the upcoming segment
     * 
     * @return Distance in meters
     */
    public double getDistanceToUpcomingSegment() {
        return distanceToUpcomingSegment;
    }
}