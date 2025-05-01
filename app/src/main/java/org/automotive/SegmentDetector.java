package org.automotive;

import java.util.LinkedList;
import java.util.Queue;

/**
 * This class is responsible for detecting different segment types (straight or
 * curve)
 * by analyzing sensor data over a window of time.
 */
public class SegmentDetector {
    // Constants for segment detection
    private static final double YAW_RATE_THRESHOLD = 1.0; // °/s - threshold for detecting a curve
    private static final double STEERING_ANGLE_THRESHOLD = 10.0; // ° - threshold for detecting a curve
    private static final int WINDOW_SIZE = 5; // Number of samples to consider for segment detection
    private static final int CONSECUTIVE_SAMPLES_REQUIRED = 3; // Number of consecutive samples exceeding threshold

    // Buffers for sensor values
    private Queue<Double> yawRateBuffer = new LinkedList<>();
    private Queue<Double> steeringAngleBuffer = new LinkedList<>();

    // Current segment type
    private SegmentType currentSegment = SegmentType.STRAIGHT; // Default to straight

    // Enumeration for segment types
    public enum SegmentType {
        STRAIGHT,
        CURVE
    }

    /**
     * Updates the detector with new sensor values and determines the segment type
     * 
     * @param yawRate       Current yaw rate value (°/s)
     * @param steeringAngle Current steering angle value (°)
     * @return The detected segment type
     */
    public SegmentType updateAndDetect(double yawRate, double steeringAngle) {
        // Add new values to buffers
        yawRateBuffer.add(Math.abs(yawRate)); // Use absolute value for threshold comparison
        steeringAngleBuffer.add(Math.abs(steeringAngle));

        // Maintain buffer size
        if (yawRateBuffer.size() > WINDOW_SIZE) {
            yawRateBuffer.remove();
        }
        if (steeringAngleBuffer.size() > WINDOW_SIZE) {
            steeringAngleBuffer.remove();
        }

        // Only start detection when we have enough samples
        if (yawRateBuffer.size() >= WINDOW_SIZE) {
            // Count consecutive samples exceeding thresholds
            int yawRateExceedCount = 0;
            int steeringAngleExceedCount = 0;

            for (Double yaw : yawRateBuffer) {
                if (yaw > YAW_RATE_THRESHOLD) {
                    yawRateExceedCount++;
                }
            }

            for (Double angle : steeringAngleBuffer) {
                if (angle > STEERING_ANGLE_THRESHOLD) {
                    steeringAngleExceedCount++;
                }
            }

            // Determine segment type based on consecutive samples
            if (yawRateExceedCount >= CONSECUTIVE_SAMPLES_REQUIRED ||
                    steeringAngleExceedCount >= CONSECUTIVE_SAMPLES_REQUIRED) {
                currentSegment = SegmentType.CURVE;
            } else {
                currentSegment = SegmentType.STRAIGHT;
            }
        }

        return currentSegment;
    }

    /**
     * Get the current detected segment type
     * 
     * @return The current segment type
     */
    public SegmentType getCurrentSegment() {
        return currentSegment;
    }

    /**
     * Reset the detector buffers and state
     */
    public void reset() {
        yawRateBuffer.clear();
        steeringAngleBuffer.clear();
        currentSegment = SegmentType.STRAIGHT;
    }
}