package org.automotive;

import java.util.ArrayDeque;
import java.util.Deque;

public class SegmentDetector {
    private Deque<Double> yawWindow = new ArrayDeque<>();
    private final int windowSize = 5;
    private final double curveThreshold = 2.0;

    public void addYawValue(double yaw) {
        if (yawWindow.size() == windowSize) yawWindow.removeFirst();
        yawWindow.addLast(yaw);
    }

    public SegmentType getCurrentSegment() {
        double avg = yawWindow.stream().mapToDouble(d -> Math.abs(d)).average().orElse(0);
        return avg > curveThreshold ? SegmentType.CURVE : SegmentType.STRAIGHT;
    }
}