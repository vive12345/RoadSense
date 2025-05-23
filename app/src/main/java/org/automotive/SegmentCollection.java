package org.automotive;

import org.automotive.utils.GPSUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class maintains a collection of road segments and provides
 * methods to add, access, and print segment data.
 */
public class SegmentCollection {
    // List to store all segments
    private List<SegmentData> segments;

    /**
     * Constructor to initialize an empty segment collection
     */
    public SegmentCollection() {
        segments = new ArrayList<>();
    }

    /**
     * Add a segment to the collection
     * 
     * @param segment The segment to add
     */
    public void addSegment(SegmentData segment) {
        segments.add(segment);
    }

    /**
     * Get the total number of segments in the collection
     * 
     * @return The number of segments
     */
    public int size() {
        return segments.size();
    }

    /**
     * Get a segment at a specific index
     * 
     * @param index The index of the segment to retrieve
     * @return The segment at the specified index, or null if the index is invalid
     */
    public SegmentData getSegment(int index) {
        if (index >= 0 && index < segments.size()) {
            return segments.get(index);
        }
        return null;
    }

    /**
     * Get all segments in the collection
     * 
     * @return List of all segments
     */
    public List<SegmentData> getAllSegments() {
        return new ArrayList<>(segments); // Return a copy to prevent modification
    }

    /**
     * Get all straight segments in the collection
     * 
     * @return List of straight segments
     */
    public List<SegmentData> getStraightSegments() {
        List<SegmentData> straightSegments = new ArrayList<>();
        for (SegmentData segment : segments) {
            if (segment.getType() == SegmentDetector.SegmentType.STRAIGHT) {
                straightSegments.add(segment);
            }
        }
        return straightSegments;
    }

    /**
     * Get all curve segments in the collection
     * 
     * @return List of curve segments
     */
    public List<SegmentData> getCurveSegments() {
        List<SegmentData> curveSegments = new ArrayList<>();
        for (SegmentData segment : segments) {
            if (segment.getType() == SegmentDetector.SegmentType.CURVE) {
                curveSegments.add(segment);
            }
        }
        return curveSegments;
    }

    /**
     * Print a summary of all segments in the collection
     */
    public void print() {
        if (segments.isEmpty()) {
            System.out.println("No segments available.");
            return;
        }

        int straightCount = 0;
        int curveCount = 0;

        for (SegmentData segment : segments) {
            if (segment.getType() == SegmentDetector.SegmentType.STRAIGHT) {
                straightCount++;
            } else {
                curveCount++;
            }
        }

        System.out.println("\n=========== SEGMENT COLLECTION SUMMARY ===========");
        System.out.println("Total segments: " + segments.size());
        System.out.println("Straight segments: " + straightCount);
        System.out.println("Curve segments: " + curveCount);
        System.out.println();

        // Print individual segments
        for (int i = 0; i < segments.size(); i++) {
            System.out.println("SEGMENT #" + (i + 1));
            segments.get(i).print();
            System.out.println();
        }
    }

    /**
     * Find the nearest upcoming segment based on current GPS position
     * 
     * @param currentPosition The current GPS position of the vehicle
     * @param currentTime     The current simulation time
     * @return The nearest upcoming segment, or null if none found
     */
    public SegmentData findNearestUpcomingSegment(GPScoordinates currentPosition, double currentTime) {
        SegmentData nearestSegment = null;
        double minDistance = Double.MAX_VALUE;

        for (SegmentData segment : segments) {
            // Only consider segments in the future (based on start time)
            if (segment.getStartTime() > currentTime) {
                double distance = GPSUtils.calculateDistance(currentPosition, segment.getStartCoordinates());

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestSegment = segment;
                }
            }
        }

        return nearestSegment;
    }
}