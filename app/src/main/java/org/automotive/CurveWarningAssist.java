package org.automotive;

import org.automotive.utils.GPSUtils;
import java.text.DecimalFormat;

/**
 * provides warnings about upcoming road segments, especially curves.
 * It uses previously recorded segment data to inform the driver about upcoming
 * road features.
 */
public class CurveWarningAssist {
    private static final double MEDIUM_DISTANCE_THRESHOLD = 100.0;
    private static final double SHORT_DISTANCE_THRESHOLD = 50.0;
    private DecimalFormat df = new DecimalFormat("0.0");
    private SegmentCollection segmentCollection;
    private SegmentData upcomingSegment;
    private double distanceToUpcomingSegment;

    /**
     * Constructor to initialize the CurveWarningAssist with saved segment data
     */
    public CurveWarningAssist(SegmentCollection segmentCollection) {
        this.segmentCollection = segmentCollection;
        this.upcomingSegment = null;
        this.distanceToUpcomingSegment = Double.MAX_VALUE;
    }

    /**
     * Update the ADAS with current vehicle position and simulation time
     */
    public String update(GPScoordinates currentPosition, double currentTime) {
        if (segmentCollection == null || currentPosition == null) {
            return "ADAS: No segment data available";
        }

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
     */
    private String generateWarningMessage() {
        StringBuilder message = new StringBuilder();

        message.append("Next ").append(upcomingSegment.getType()).append(": ");
        message.append(df.format(distanceToUpcomingSegment)).append("m");

        if (upcomingSegment.getType() == SegmentDetector.SegmentType.CURVE) {
            message.append(" (").append(upcomingSegment.getCurveDirection()).append(")");

            if (distanceToUpcomingSegment <= SHORT_DISTANCE_THRESHOLD) {
                message.append(" [!] IMMEDIATE CURVE");
            } else if (distanceToUpcomingSegment <= MEDIUM_DISTANCE_THRESHOLD) {
                message.append(" [!] PREPARE FOR CURVE");
            }

            if (upcomingSegment.getAverageSpeed() < 30.0) {
                message.append(" - Reduce speed");
            }
        }

        return message.toString();
    }

    /**
     * Check if an upcoming segment is detected
     */
    public boolean hasUpcomingSegment() {
        return upcomingSegment != null;
    }

    /**
     * Get the upcoming segment
     */
    public SegmentData getUpcomingSegment() {
        return upcomingSegment;
    }

    /**
     * Get the distance to the upcoming segment
     */
    public double getDistanceToUpcomingSegment() {
        return distanceToUpcomingSegment;
    }
}