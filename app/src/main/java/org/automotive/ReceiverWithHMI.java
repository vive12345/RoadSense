package org.automotive;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced receiver application with ADAS Curve Warning System and graphical
 * HMI interface.
 * This class handles socket communication with the simulator, processes sensor
 * data,
 * detects road segments, and provides warnings about upcoming road features.
 */
public class ReceiverWithHMI {
    // Network configuration
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 54000;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean running = false;
    private long simulationStartTime;

    // Sensor data
    private double steeringAngle = 0.0;
    private double vehicleSpeed = 0.0;
    private double yawRate = 0.0;
    private double longAccel = 0.0;
    private double latAccel = 0.0;
    private GPScoordinates currentGPS = null;
    private double currentSimTime = 0.0;

    // Display values
    private String steeringAngleStr = "-";
    private String vehicleSpeedStr = "-";
    private String yawRateStr = "-";
    private String longAccelStr = "-";
    private String latAccelStr = "-";
    private String gpsLatitudeStr = "-";
    private String gpsLongitudeStr = "-";

    // Segment detection
    private SegmentDetector segmentDetector = new SegmentDetector();
    private SegmentCollection segments = new SegmentCollection();
    private SegmentData currentSegment = null;
    private SegmentDetector.SegmentType lastSegmentType = null;
    private double lastHeading = 0.0;
    private List<GPScoordinates> gpsBuffer = new ArrayList<>();

    // ADAS system
    private SegmentCollection savedSegments = null;
    private CurveWarningAssist curveWarningAssist = null;
    private boolean isFirstRun = true;
    private ADASInterface adasInterface;

    // Formatting and message handling
    private final DecimalFormat df = new DecimalFormat("0.0");
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1000);

    public static void main(String[] args) {
        new ReceiverWithHMI().runMultipleSimulations();
    }

    /**
     * Runs multiple simulations as requested by the user
     */
    public void runMultipleSimulations() {
        boolean continueRunning = true;
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (continueRunning) {
            resetSensorValues();
            initializeHMI();
            start();

            if (isFirstRun && segments.size() > 0) {
                savedSegments = segments;
                isFirstRun = false;
                System.out.println("\nSegment data saved for future runs. Detected " + segments.size() + " segments.");
                segments.print();
                curveWarningAssist = new CurveWarningAssist(savedSegments);
            }

            try {
                System.out.println("\nDo you want to run another simulation? (y/n)");
                String response = consoleReader.readLine().trim().toLowerCase();
                continueRunning = response.equals("y") || response.equals("yes");
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
                continueRunning = false;
            }
        }

        System.out.println("Exiting application. Goodbye!");
    }

    private void initializeHMI() {
        adasInterface = new ADASInterface(isFirstRun);
    }

    private void resetSensorValues() {
        steeringAngle = 0.0;
        vehicleSpeed = 0.0;
        yawRate = 0.0;
        longAccel = 0.0;
        latAccel = 0.0;
        currentGPS = null;
        currentSimTime = 0.0;

        steeringAngleStr = "-";
        vehicleSpeedStr = "-";
        yawRateStr = "-";
        longAccelStr = "-";
        latAccelStr = "-";
        gpsLatitudeStr = "-";
        gpsLongitudeStr = "-";

        segmentDetector.reset();
        segments = new SegmentCollection();
        currentSegment = null;
        lastSegmentType = null;
        gpsBuffer.clear();
    }

    /**
     * Starts a simulation run
     */
    public void start() {
        System.out.println("<==== Simulation Receiver with HMI Started ====>");
        System.out.println(isFirstRun ? "First run - segment data will be collected."
                : "Using segment data from previous run for ADAS Curve Warning System.");
        System.out.println("Press ENTER to connect to simulator and start the simulation...");

        try {
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            System.out.println("Starting simulation...");

            if (connectToSimulator()) {
                running = true;
                simulationStartTime = System.nanoTime();

                Thread receiveThread = new Thread(this::socketReceive);
                Thread processThread = new Thread(this::messageProcessing);

                receiveThread.setPriority(Thread.MAX_PRIORITY);
                processThread.setPriority(Thread.NORM_PRIORITY);

                receiveThread.start();
                processThread.start();
                receiveThread.join();
                processThread.join();

                System.out.println("\n<========== Simulation completed ========>");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error in Receiver application: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private boolean connectToSimulator() {
        try {
            System.out.println("Connecting to simulator at " + SERVER_ADDRESS + ":" + SERVER_PORT + "...");
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(8192);

            out = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String headerFormat = isFirstRun
                    ? "Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment"
                    : "Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment | ADAS Info";
            System.out.println(headerFormat);

            out.println("START");
            out.flush();
            return true;
        } catch (IOException e) {
            System.out.println("Failed to connect to simulator: " + e.getMessage());
            return false;
        }
    }

    private void socketReceive() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                if (message.equals("SIMULATION_COMPLETE")) {
                    System.out.println("\nReceived simulation complete signal from simulator.");
                    if (currentSegment != null) {
                        finalizeCurrentSegment();
                    }
                    running = false;
                    break;
                }

                boolean added = messageQueue.offer(message, 50, TimeUnit.MILLISECONDS);
                if (!added) {
                    System.out.println("\nWarning: Message queue full, dropped message: " + message);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("\nError reading from socket: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.out.println("\nMessage queue interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
    }

    private void messageProcessing() {
        try (BufferedWriter logFile = new BufferedWriter(new FileWriter("simulation_log.txt"))) {
            logFile.write("Message ID | Time Offset | Values | System Time Delta\n");
            logFile.write("---------------------------------------------------\n");

            while (running || !messageQueue.isEmpty()) {
                try {
                    String message = messageQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        long currentTimeNanos = System.nanoTime();
                        long timeDeltaMillis = TimeUnit.NANOSECONDS.toMillis(currentTimeNanos - simulationStartTime);

                        processMessage(message, timeDeltaMillis, logFile);
                        detectSegment();
                        updateADASInfo();
                        updateConsoleDisplay();
                    }
                } catch (InterruptedException e) {
                    System.out.println("\nMessage processing interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            System.out.println("\nIssue encountered while writing to log file: " + e.getMessage());
        }
    }

    private void updateADASInfo() {
        if (adasInterface == null)
            return;

        adasInterface.updateVehicleData(
                vehicleSpeed, steeringAngle, yawRate, latAccel, longAccel, lastSegmentType, currentSimTime);

        if (!isFirstRun && curveWarningAssist != null && currentGPS != null) {
            String adasMessage = curveWarningAssist.update(currentGPS, currentSimTime);

            if (curveWarningAssist.hasUpcomingSegment()) {
                SegmentData upcomingSegment = curveWarningAssist.getUpcomingSegment();
                double distance = curveWarningAssist.getDistanceToUpcomingSegment();
                adasInterface.updateADASWarning(upcomingSegment, distance);
            } else {
                adasInterface.updateADASWarning(null, Double.MAX_VALUE);
            }
        }
    }

    private void processMessage(String message, long timeDelta, Writer logFile) throws IOException {
        String[] parts = message.split("\\|");
        if (parts.length < 2)
            return;

        if (parts[0].trim().equals("CAN")) {
            if (parts.length < 4)
                return;

            String id = parts[1];
            double timeOffset = Double.parseDouble(parts[2]);
            currentSimTime = timeOffset;

            String type = parts[3];
            switch (type) {
                case "STEERING":
                    if (parts.length > 4) {
                        steeringAngleStr = extractValue(parts[4], "Angle=", "°");
                        try {
                            steeringAngle = Double.parseDouble(steeringAngleStr);
                        } catch (NumberFormatException e) {
                            /* Keep previous value */ }
                    }
                    break;
                case "SPEED":
                    if (parts.length > 4) {
                        vehicleSpeedStr = extractValue(parts[4], "Speed=", " km/h");
                        try {
                            vehicleSpeed = Double.parseDouble(vehicleSpeedStr);
                        } catch (NumberFormatException e) {
                            /* Keep previous value */ }
                    }
                    break;
                case "DYNAMICS":
                    if (parts.length > 6) {
                        yawRateStr = parts[4];
                        longAccelStr = parts[5];
                        latAccelStr = parts[6];

                        try {
                            yawRate = Double.parseDouble(yawRateStr);
                            longAccel = Double.parseDouble(longAccelStr);
                            latAccel = Double.parseDouble(latAccelStr);
                        } catch (NumberFormatException e) {
                            /* Keep previous values */ }
                    }
                    break;
            }
        } else if (parts[0].trim().equals("GPS")) {
            if (parts.length < 4)
                return;

            double timeOffset = Double.parseDouble(parts[1]);
            currentSimTime = timeOffset;
            gpsLatitudeStr = parts[2];
            gpsLongitudeStr = parts[3];

            try {
                double latitude = Double.parseDouble(gpsLatitudeStr);
                double longitude = Double.parseDouble(gpsLongitudeStr);
                GPScoordinates newGPS = new GPScoordinates(latitude, longitude, timeOffset);
                currentGPS = newGPS;

                // Maintain GPS buffer for heading calculation
                gpsBuffer.add(newGPS);
                if (gpsBuffer.size() > 2) {
                    gpsBuffer.remove(0);
                }

                // Calculate heading with at least 2 GPS points
                if (gpsBuffer.size() >= 2) {
                    lastHeading = SegmentCollection.calculateHeading(gpsBuffer.get(0), gpsBuffer.get(1));
                }
            } catch (NumberFormatException e) {
                /* Keep previous values */ }
        }

        logFile.write(message + " | " + timeDelta + "ms\n");
        logFile.flush();
    }

    private void detectSegment() {
        // Only proceed with valid sensor data
        if (steeringAngleStr.equals("-") || yawRateStr.equals("-") || currentGPS == null) {
            return;
        }

        SegmentDetector.SegmentType currentType = segmentDetector.updateAndDetect(yawRate, steeringAngle);
        SegmentDetector.CurveDirection curveDir = segmentDetector.getCurveDirection();

        // Handle segment transitions
        if (lastSegmentType != null && currentType != lastSegmentType) {
            if (currentSegment != null) {
                currentSegment.addGPSCoordinate(currentGPS);
                currentSegment.addHeading(lastHeading);
                finalizeCurrentSegment();
            }

            // Start new segment
            currentSegment = new SegmentData(currentType, currentSimTime, currentGPS, lastHeading);
            if (currentType == SegmentDetector.SegmentType.CURVE && curveDir != SegmentDetector.CurveDirection.NONE) {
                currentSegment.setCurveDirection(curveDir == SegmentDetector.CurveDirection.LEFT ? "left" : "right");
            }
        } else if (lastSegmentType == null) {
            // First segment
            currentSegment = new SegmentData(currentType, currentSimTime, currentGPS, lastHeading);
            if (currentType == SegmentDetector.SegmentType.CURVE && curveDir != SegmentDetector.CurveDirection.NONE) {
                currentSegment.setCurveDirection(curveDir == SegmentDetector.CurveDirection.LEFT ? "left" : "right");
            }
        } else if (currentType == SegmentDetector.SegmentType.CURVE &&
                curveDir != SegmentDetector.CurveDirection.NONE &&
                currentSegment != null) {
            // Update curve direction during a curve
            currentSegment.setCurveDirection(curveDir == SegmentDetector.CurveDirection.LEFT ? "left" : "right");
        }

        // Update current segment data
        if (currentSegment != null) {
            currentSegment.addGPSCoordinate(currentGPS);
            currentSegment.addHeading(lastHeading);
            currentSegment.addSpeedValue(vehicleSpeed);

            if (currentType == SegmentDetector.SegmentType.STRAIGHT) {
                currentSegment.addLongitudinalAcceleration(longAccel);
            } else {
                currentSegment.addLateralAcceleration(latAccel);
            }

            currentSegment.addYawRateValue(yawRate);
        }

        lastSegmentType = currentType;
    }

    private void finalizeCurrentSegment() {
        if (currentSegment != null && currentGPS != null) {
            currentSegment.finalizeSegment(currentSimTime, currentGPS, lastHeading);
            segments.addSegment(currentSegment);
            System.out.println("\nDetected new " + currentSegment.getType() +
                    " segment. Total segments: " + segments.size());
            currentSegment = null;
        }
    }

    private String extractValue(String input, String prefix, String suffix) {
        int start = input.indexOf(prefix);
        if (start == -1)
            return "-";

        start += prefix.length();
        int end = input.indexOf(suffix, start);
        if (end == -1)
            return "-";

        try {
            double value = Double.parseDouble(input.substring(start, end).trim());
            return df.format(value);
        } catch (NumberFormatException e) {
            return input.substring(start, end).trim();
        }
    }

    private void updateConsoleDisplay() {
        DecimalFormat timeFormat = new DecimalFormat("#,##0.0");
        String timeStr = timeFormat.format(currentSimTime) + " ms";
        String speedStr = vehicleSpeedStr.equals("-") ? "-" : vehicleSpeedStr + " km/h";
        String steerStr = steeringAngleStr.equals("-") ? "-" : steeringAngleStr + " deg";

        // Format yaw rate
        String yawStr;
        if (yawRateStr.equals("-")) {
            yawStr = "-";
        } else {
            try {
                yawStr = df.format(Double.parseDouble(yawRateStr)) + "°/s";
            } catch (NumberFormatException e) {
                yawStr = yawRateStr + "°/s";
            }
        }

        // Format accelerations
        String latStr = formatAcceleration(latAccelStr);
        String longStr = formatAcceleration(longAccelStr);

        // Format GPS
        String gpsStr;
        if (gpsLatitudeStr.equals("-") || gpsLongitudeStr.equals("-")) {
            gpsStr = "-";
        } else {
            try {
                DecimalFormat gpsFormat = new DecimalFormat("0.000000");
                gpsStr = gpsFormat.format(Double.parseDouble(gpsLatitudeStr)) + ", " +
                        gpsFormat.format(Double.parseDouble(gpsLongitudeStr));
            } catch (NumberFormatException e) {
                gpsStr = gpsLatitudeStr + ", " + gpsLongitudeStr;
            }
        }

        String segmentStr = (lastSegmentType != null) ? lastSegmentType.toString() : "-";
        String adasInfo = "";

        if (!isFirstRun && curveWarningAssist != null && currentGPS != null) {
            adasInfo = curveWarningAssist.update(currentGPS, currentSimTime);
        } else {
            adasInfo = "ADAS: Data collection in progress";
        }

        // Format display line
        String displayLine;
        if (isFirstRun) {
            displayLine = String.format(
                    "\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %-25s | %s",
                    timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr, segmentStr);
        } else {
            displayLine = String.format(
                    "\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %-25s | %-8s | %s",
                    timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr, segmentStr, adasInfo);
        }

        System.out.print(displayLine);
        System.out.flush();
    }

    private String formatAcceleration(String accelStr) {
        if (accelStr.equals("-")) {
            return "-";
        }
        try {
            return df.format(Double.parseDouble(accelStr)) + " m/s²";
        } catch (NumberFormatException e) {
            return accelStr + " m/s²";
        }
    }

    private void closeConnection() {
        running = false;
        try {
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (socket != null && !socket.isClosed())
                socket.close();
            System.out.println("Connection closed");
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}