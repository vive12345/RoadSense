package org.automotive;

import org.automotive.utils.GPSUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Receiver that adds road segment detection and data extraction.
 */
public class ReceiverEnhanced extends ReceiverBase {
    // Segment detection related objects
    protected SegmentDetector segmentDetector = new SegmentDetector();
    protected SegmentCollection segments = new SegmentCollection();
    protected SegmentData currentSegment = null;
    protected SegmentDetector.SegmentType lastSegmentType = null;
    protected double lastHeading = 0.0; // Track vehicle heading
    protected List<GPScoordinates> gpsBuffer = new ArrayList<>(); // Buffer for heading calculation

    // For ADAS
    protected SegmentCollection savedSegments = null; // Segments from previous run
    protected CurveWarningAssist curveWarningAssist = null; // ADAS system
    protected boolean isFirstRun = true;

    /**
     * Main method to run the Enhanced Receiver application
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        ReceiverEnhanced receiver = new ReceiverEnhanced();
        receiver.runMultipleSimulations();
    }

    @Override
    protected void resetSensorValues() {
        super.resetSensorValues();

        // Reset segment detection
        segmentDetector.reset();
        segments = new SegmentCollection();
        currentSegment = null;
        lastSegmentType = null;
        gpsBuffer.clear();
    }

    @Override
    protected void showWelcomeMessage() {
        System.out.println("<==== Simulation Receiver Started ====>");
        if (isFirstRun) {
            System.out.println("First run - segment data will be collected.");
        } else {
            System.out.println("Using segment data from previous run for ADAS Curve Warning System.");
        }
        System.out.println("Press ENTER to connect to simulator and start the simulation...");
    }

    @Override
    protected void printConsoleHeader() {
        if (isFirstRun) {
            System.out.println(
                    "Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment");
        } else {
            System.out.println(
                    "Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment | ADAS Info");
        }
    }

    @Override
    protected void processGPSMessage(String[] parts) {
        super.processGPSMessage(parts);

        // Additional GPS processing for heading calculation
        if (currentGPS != null) {
            // Add to GPS buffer for heading calculation
            gpsBuffer.add(currentGPS);
            if (gpsBuffer.size() > 2) {
                gpsBuffer.remove(0); // Keep only the 2 most recent coordinates
            }

            // Calculate heading if we have at least 2 GPS points
            if (gpsBuffer.size() >= 2) {
                GPScoordinates prev = gpsBuffer.get(0);
                GPScoordinates curr = gpsBuffer.get(1);
                lastHeading = GPSUtils.calculateHeading(prev, curr);
            }
        }
    }

    @Override
    protected void processAdditionalData() {
        // Perform segment detection and data extraction
        detectSegment();
    }

    @Override
    protected void beforeSimulationComplete() {
        // Finalize the last segment if there is one
        if (currentSegment != null) {
            finalizeCurrentSegment();
        }
    }

    @Override
    protected void afterSimulation() {
        // After the simulation, save segments for future runs
        if (isFirstRun && segments.size() > 0) {
            savedSegments = segments;
            isFirstRun = false;
            System.out.println("\nSegment data saved for future runs. Detected " + segments.size() + " segments.");

            // Print detailed segment data after first run
            segments.print();

            // Initialize Curve Warning Assist for future runs
            curveWarningAssist = new CurveWarningAssist(savedSegments);
        }
    }

    @Override
    protected void updateConsoleDisplay() {
        String[] formattedValues = formatDisplayValues();

        // Get current segment type
        String segmentStr = (lastSegmentType != null) ? lastSegmentType.toString() : "-";

        // Get ADAS information if we have saved segments from a previous run
        String adasInfo = "";
        if (!isFirstRun && curveWarningAssist != null && currentGPS != null) {
            // Update the ADAS system with current position and get warning
            adasInfo = curveWarningAssist.update(currentGPS, currentSimTime);
        } else {
            adasInfo = "ADAS: Data collection in progress";
        }

        // Create the display line with proper spacing
        String displayLine;
        if (isFirstRun) {
            displayLine = String.format("\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %-25s | %s",
                    formattedValues[0], formattedValues[1], formattedValues[2],
                    formattedValues[3], formattedValues[4], formattedValues[5],
                    formattedValues[6], segmentStr);
        } else {
            displayLine = String.format("\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %-25s | %-8s | %s",
                    formattedValues[0], formattedValues[1], formattedValues[2],
                    formattedValues[3], formattedValues[4], formattedValues[5],
                    formattedValues[6], segmentStr, adasInfo);
        }

        // Print the line with carriage return to update in place
        System.out.print(displayLine);
        System.out.flush();
    }

    /**
     * Detect road segments based on sensor data and extract segment-specific data
     */
    protected void detectSegment() {
        // Only start detection when we have all necessary sensor data
        if (steeringAngleStr.equals("-") || yawRateStr.equals("-") || currentGPS == null) {
            return;
        }

        // Detect segment type using the detector
        SegmentDetector.SegmentType currentType = segmentDetector.updateAndDetect(yawRate, steeringAngle);
        SegmentDetector.CurveDirection curveDir = segmentDetector.getCurveDirection();

        // Check if segment type has changed
        if (lastSegmentType != null && currentType != lastSegmentType) {
            // Segment type changed, finalize the current segment
            if (currentSegment != null) {
                // Always add GPS coordinate to track the path
                currentSegment.addGPSCoordinate(currentGPS);

                // Add heading for trajectory calculation
                currentSegment.addHeading(lastHeading);
                finalizeCurrentSegment();
            }

            // Start a new segment
            currentSegment = new SegmentData(currentType, currentSimTime, currentGPS, lastHeading);

            // Set curve direction if we're entering a curve
            if (currentType == SegmentDetector.SegmentType.CURVE && curveDir != SegmentDetector.CurveDirection.NONE) {
                currentSegment.setCurveDirection(curveDir == SegmentDetector.CurveDirection.LEFT ? "left" : "right");
            }
        } else if (lastSegmentType == null) {
            // First segment, start it
            currentSegment = new SegmentData(currentType, currentSimTime, currentGPS, lastHeading);

            // Set curve direction if it's a curve
            if (currentType == SegmentDetector.SegmentType.CURVE && curveDir != SegmentDetector.CurveDirection.NONE) {
                currentSegment.setCurveDirection(curveDir == SegmentDetector.CurveDirection.LEFT ? "left" : "right");
            }
        } else if (currentType == SegmentDetector.SegmentType.CURVE &&
                curveDir != SegmentDetector.CurveDirection.NONE &&
                currentSegment != null) {
            // Update curve direction continually during a curve (in case it changes)
            currentSegment.setCurveDirection(curveDir == SegmentDetector.CurveDirection.LEFT ? "left" : "right");
        }

        // Update the current segment with sensor data
        if (currentSegment != null) {
            // Always add GPS coordinate to track the path
            currentSegment.addGPSCoordinate(currentGPS);

            // Add heading for trajectory calculation
            currentSegment.addHeading(lastHeading);

            // Always add speed value for all segment types
            currentSegment.addSpeedValue(vehicleSpeed);

            // Add acceleration data based on segment type
            if (currentType == SegmentDetector.SegmentType.STRAIGHT) {
                // For straight segments, track longitudinal acceleration
                currentSegment.addLongitudinalAcceleration(longAccel);
            } else {
                // For curves, track lateral acceleration
                currentSegment.addLateralAcceleration(latAccel);
            }

            // Add yaw rate data (especially important for curves)
            currentSegment.addYawRateValue(yawRate);
        }

        // Update last segment type
        lastSegmentType = currentType;
    }

    /**
     * Finalize the current segment, calculate all derived data, and add it to the
     * collection
     */
    protected void finalizeCurrentSegment() {
        if (currentSegment != null && currentGPS != null) {
            // Finalize segment with current GPS and heading
            currentSegment.finalizeSegment(currentSimTime, currentGPS, lastHeading);

            // Add to segment collection
            segments.addSegment(currentSegment);

            // Print information about the new segment
            System.out.println(
                    "\nDetected new " + currentSegment.getType() + " segment. Total segments: " + segments.size());

            // Reset current segment
            currentSegment = null;
        }
    }
}