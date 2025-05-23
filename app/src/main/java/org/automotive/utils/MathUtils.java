package org.automotive.utils;

import java.util.List;

/**
 * Utility class for general mathematical operations.
 */
public class MathUtils {

    private MathUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Calculate the average of a list of values
     */
    public static double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (Double value : values) {
            sum += value;
        }

        return sum / values.size();
    }

    /**
     * Find the maximum value in a list
     */
    public static double findMax(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.MIN_VALUE;
        }

        double max = Double.MIN_VALUE;
        for (Double value : values) {
            if (value > max) {
                max = value;
            }
        }

        return max;
    }

    /**
     * Find the minimum value in a lis
     */
    public static double findMin(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double min = Double.MAX_VALUE;
        for (Double value : values) {
            if (value < min) {
                min = value;
            }
        }

        return min;
    }

    /**
     * Count maximum consecutive values over threshold
     */
    public static int countMaxConsecutiveOverThreshold(Double[] values, double threshold) {
        if (values == null || values.length == 0) {
            return 0;
        }

        int maxConsecutive = 0;
        int currentConsecutive = 0;

        for (double value : values) {
            if (value > threshold) {
                currentConsecutive++;
                maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
            } else {
                currentConsecutive = 0;
            }
        }

        return maxConsecutive;
    }
}