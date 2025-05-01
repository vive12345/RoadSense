package org.automotive;

import java.util.ArrayList;
import java.util.List;

public class SegmentManager {
    private final List<SegmentData> segments = new ArrayList<>();
    private SegmentData currentSegment;
    private SegmentType currentType;
    private GPScoordinates lastGPS;

    public void update(SegmentType newType, GPScoordinates gps, double speed, double yawRate, double longAccel, double latAccel) {
        // If this is the first segment, or the segment type has changed, finalize current
        if (currentSegment == null || newType != currentType) {
            if (currentSegment != null) {
                currentSegment.endGPS = lastGPS;
                currentSegment.endHeading = SegmentData.bearing(
                        currentSegment.startGPS.getLatitude(),
                        currentSegment.startGPS.getLongitude(),
                        currentSegment.endGPS.getLatitude(),
                        currentSegment.endGPS.getLongitude()
                );
                segments.add(currentSegment);
            }

            // Start new segment
            currentSegment = new SegmentData();
            currentSegment.type = newType;
            currentSegment.startGPS = gps;
            currentSegment.startHeading = lastGPS == null ? 0.0 :
                    SegmentData.bearing(lastGPS.getLatitude(), lastGPS.getLongitude(), gps.getLatitude(), gps.getLongitude());
            currentType = newType;
            currentSegment.totalDistance = 0.0;
        }

        // Accumulate data
        currentSegment.speeds.add(speed);
        currentSegment.yawRates.add(yawRate);
        currentSegment.longAccels.add(longAccel);
        currentSegment.latAccels.add(latAccel);

        // Accumulate distance
        if (lastGPS != null) {
            double dist = SegmentData.haversine(
                    lastGPS.getLatitude(), lastGPS.getLongitude(),
                    gps.getLatitude(), gps.getLongitude()
            );
            currentSegment.totalDistance += dist;
        }

        lastGPS = gps;
    }

    public void finalizeSegment() {
        if (currentSegment != null) {
            currentSegment.endGPS = lastGPS;
            currentSegment.endHeading = SegmentData.bearing(
                    currentSegment.startGPS.getLatitude(),
                    currentSegment.startGPS.getLongitude(),
                    lastGPS.getLatitude(),
                    lastGPS.getLongitude()
            );
            segments.add(currentSegment);
            currentSegment = null;
        }
    }

    public SegmentData findUpcomingSegment(GPScoordinates currentGPS, double thresholdMeters) {
        if (currentGPS == null) return null;

        for (SegmentData segment : segments) {
            double dist = SegmentData.haversine(
                    currentGPS.getLatitude(), currentGPS.getLongitude(),
                    segment.startGPS.getLatitude(), segment.startGPS.getLongitude());

            if (dist <= thresholdMeters) {
                return segment;
            }
        }

        return null; // No segment nearby
    }

    public List<SegmentData> getSegments() {
        return segments;
    }

    public void printAll() {
        System.out.println("\n=========== SEGMENT SUMMARY ===========\n");
        for (SegmentData segment : segments) {
            segment.print();
            System.out.println("----------------------------------------");
        }
    }
}
