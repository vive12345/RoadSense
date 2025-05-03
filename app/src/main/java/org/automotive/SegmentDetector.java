package org.automotive;

import org.automotive.utils.MathUtils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Arrays;

/**
 * Enhanced SegmentDetector with improved curve direction detection and
 * stability.
 */
public class SegmentDetector {
    // Constants for segment detection
    private static final double YAW_RATE_THRESHOLD = 1.0; // °/s - threshold for detecting a curve
    private static final double STEERING_ANGLE_THRESHOLD = 10.0; // ° - threshold for detecting a curve
    private static final int WINDOW_SIZE = 10; // Increased window size for better stability
    private static final int CONSECUTIVE_SAMPLES_REQUIRED = 3; // Number of consecutive samples exceeding threshold

    // Add these new variables for time-based stability
    private long lastSegmentChangeTime = 0;
    private static final long MINIMUM_SEGMENT_DURATION_MS = 500; // 500ms minimum segment duration

    // Buffers for sensor values
    private Queue<Double> yawRateBuffer = new LinkedList<>();
    private Queue<Double> steeringAngleBuffer = new LinkedList<>();

    // Track raw values (not absolute) to determine direction
    private Queue<Double> rawYawRateBuffer = new LinkedList<>();
    private Queue<Double> rawSteeringAngleBuffer = new LinkedList<>();

    // Current segment type and direction
    private SegmentType currentSegment = SegmentType.STRAIGHT;
    private CurveDirection curveDirection = CurveDirection.NONE;

    // Enumeration for segment types
    public enum SegmentType {
        STRAIGHT,
        CURVE
    }

    // Enumeration for curve direction
    public enum CurveDirection {
        LEFT,
        RIGHT,
        NONE
    }

    /**
     * Updates the detector with new sensor values and determines the segment type
     * and curve direction
     * 
     * @param yawRate       Current yaw rate value (°/s)
     * @param steeringAngle Current steering wheel angle (°)
     * @return The detected segment type
     */
    public SegmentType updateAndDetect(double yawRate, double steeringAngle) {
        // Add raw values to direction buffers
        rawYawRateBuffer.add(yawRate);
        rawSteeringAngleBuffer.add(steeringAngle);

        // Add absolute values to threshold buffers
        yawRateBuffer.add(Math.abs(yawRate));
        steeringAngleBuffer.add(Math.abs(steeringAngle));

        // Maintain buffer sizes
        if (yawRateBuffer.size() > WINDOW_SIZE) {
            yawRateBuffer.remove();
            rawYawRateBuffer.remove();
        }
        if (steeringAngleBuffer.size() > WINDOW_SIZE) {
            steeringAngleBuffer.remove();
            rawSteeringAngleBuffer.remove();
        }

        // Only start detection when we have enough samples
        if (yawRateBuffer.size() >= WINDOW_SIZE) {
            // Convert queues to arrays for easier analysis
            Double[] yawArray = yawRateBuffer.toArray(new Double[0]);
            Double[] steeringArray = steeringAngleBuffer.toArray(new Double[0]);
            Double[] rawYawArray = rawYawRateBuffer.toArray(new Double[0]);
            Double[] rawSteeringArray = rawSteeringAngleBuffer.toArray(new Double[0]);

            // Find maximum consecutive samples exceeding threshold
            int maxConsecutiveYaw = MathUtils.countMaxConsecutiveOverThreshold(yawArray, YAW_RATE_THRESHOLD);
            int maxConsecutiveSteering = MathUtils.countMaxConsecutiveOverThreshold(steeringArray,
                    STEERING_ANGLE_THRESHOLD);

            // Determine new segment type based on thresholds
            SegmentType newSegmentType;
            if (maxConsecutiveYaw >= CONSECUTIVE_SAMPLES_REQUIRED ||
                    maxConsecutiveSteering >= CONSECUTIVE_SAMPLES_REQUIRED) {
                newSegmentType = SegmentType.CURVE;

                // Determine curve direction based on sign of values
                updateCurveDirection(rawYawArray, rawSteeringArray);
            } else {
                newSegmentType = SegmentType.STRAIGHT;
                curveDirection = CurveDirection.NONE;
            }

            // Only change segment type if it's different AND enough time has passed
            if (newSegmentType != currentSegment) {
                long currentTimeMs = System.currentTimeMillis();
                if (currentTimeMs - lastSegmentChangeTime >= MINIMUM_SEGMENT_DURATION_MS) {
                    // Only then allow segment change
                    currentSegment = newSegmentType;
                    lastSegmentChangeTime = currentTimeMs;
                }
            }
        }

        return currentSegment;
    }

    /**
     * Update the curve direction based on raw yaw rate and steering angle
     * 
     * @param rawYawArray      Raw yaw rate values
     * @param rawSteeringArray Raw steering angle values
     */
    private void updateCurveDirection(Double[] rawYawArray, Double[] rawSteeringArray) {
        // Count positive and negative values
        int positiveYaw = 0;
        int negativeYaw = 0;
        int positiveSteering = 0;
        int negativeSteering = 0;

        // Consider only the most recent values (last half of window)
        int startIdx = WINDOW_SIZE / 2;

        for (int i = startIdx; i < WINDOW_SIZE; i++) {
            // Count yaw rate signs
            if (rawYawArray[i] > YAW_RATE_THRESHOLD) {
                positiveYaw++;
            } else if (rawYawArray[i] < -YAW_RATE_THRESHOLD) {
                negativeYaw++;
            }

            // Count steering angle signs
            if (rawSteeringArray[i] > STEERING_ANGLE_THRESHOLD) {
                positiveSteering++;
            } else if (rawSteeringArray[i] < -STEERING_ANGLE_THRESHOLD) {
                negativeSteering++;
            }
        }

        // Per CAN data convention, positive yaw rate means clockwise (right curve)
        // Positive steering angle means clockwise (right turn)

        // Determine direction based on both yaw rate and steering angle
        boolean yawIndicatesRight = positiveYaw > negativeYaw;
        boolean steeringIndicatesRight = positiveSteering > negativeSteering;

        // If both sensors agree, use that direction
        if (yawIndicatesRight == steeringIndicatesRight) {
            curveDirection = yawIndicatesRight ? CurveDirection.RIGHT : CurveDirection.LEFT;
        } else {
            // If sensors disagree, prioritize yaw rate (more reliable for curve detection)
            curveDirection = yawIndicatesRight ? CurveDirection.RIGHT : CurveDirection.LEFT;
        }
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
     * Get the current curve direction
     * 
     * @return The current curve direction
     */
    public CurveDirection getCurveDirection() {
        return curveDirection;
    }

    /**
     * Reset the detector buffers and state
     */
    public void reset() {
        yawRateBuffer.clear();
        steeringAngleBuffer.clear();
        rawYawRateBuffer.clear();
        rawSteeringAngleBuffer.clear();
        currentSegment = SegmentType.STRAIGHT;
        curveDirection = CurveDirection.NONE;
        lastSegmentChangeTime = 0;
    }
}