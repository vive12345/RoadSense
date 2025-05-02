package org.automotive;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * This class extends the ReceiverEnhanced class to add a graphical HMI
 * for the ADAS Curve Warning System.
 */
public class ReceiverWithHMI {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 54000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean running = false;
    private long simulationStartTime;

    // Data structures to store the latest values for each sensor
    private double steeringAngle = 0.0;
    private double vehicleSpeed = 0.0;
    private double yawRate = 0.0;
    private double longAccel = 0.0;
    private double latAccel = 0.0;
    private GPScoordinates currentGPS = null;
    private double currentSimTime = 0.0;

    // Store string representations for display
    private String steeringAngleStr = "-";
    private String vehicleSpeedStr = "-";
    private String yawRateStr = "-";
    private String longAccelStr = "-";
    private String latAccelStr = "-";
    private String gpsLatitudeStr = "-";
    private String gpsLongitudeStr = "-";

    // Segment detection related objects
    private SegmentDetector segmentDetector = new SegmentDetector();
    private SegmentCollection segments = new SegmentCollection();
    private SegmentData currentSegment = null;
    private SegmentDetector.SegmentType lastSegmentType = null;
    private double lastHeading = 0.0; // Track vehicle heading
    private List<GPScoordinates> gpsBuffer = new ArrayList<>(); // Buffer for heading calculation

    // For ADAS
    private SegmentCollection savedSegments = null; // Segments from previous run
    private CurveWarningAssist curveWarningAssist = null; // ADAS system
    private boolean isFirstRun = true;

    // HMI Interface
    private ADASInterface adasInterface;

    // Formatter for decimal values - limit to 1 decimal place for display
    private DecimalFormat df = new DecimalFormat("0.0");

    // Create a thread-safe queue for message processing with larger capacity
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1000);

    /**
     * Main method to run the Receiver application
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        ReceiverWithHMI receiver = new ReceiverWithHMI();
        // Run the receiver in a loop to allow multiple simulations
        receiver.runMultipleSimulations();
    }

    /**
     * Allows running multiple simulations without restarting the application
     */
    public void runMultipleSimulations() {
        boolean continueRunning = true;
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (continueRunning) {
            // Reset sensor values between simulations
            resetSensorValues();

            // Initialize the HMI
            initializeHMI();

            // Start a new simulation
            start();

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

            // Ask if user wants to run another simulation
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

    /**
     * Initialize the HMI interface
     */
    private void initializeHMI() {
        // Create new ADAS Interface with current run status
        adasInterface = new ADASInterface(isFirstRun);
    }

    /**
     * Resets all sensor values to their default state
     */
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

        // Reset segment detection
        segmentDetector.reset();
        segments = new SegmentCollection();
        currentSegment = null;
        lastSegmentType = null;
        gpsBuffer.clear();
    }

    /**
     * Starts the Receiver application
     */
    public void start() {
        // Show welcome message and wait for user input to start
        System.out.println("<==== Simulation Receiver with HMI Started ====>");
        if (isFirstRun) {
            System.out.println("First run - segment data will be collected.");
        } else {
            System.out.println("Using segment data from previous run for ADAS Curve Warning System.");
        }
        System.out.println("Press ENTER to connect to simulator and start the simulation...");

        try {
            // Wait for user to press Enter
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            System.out.println("Starting simulation...");

            // Connect to simulator
            if (connectToSimulator()) {
                running = true;
                simulationStartTime = System.nanoTime(); // Use nanoTime for precision

                // Start threads for receiving and processing messages
                Thread receiveThread = new Thread(this::socketReceive);
                Thread processThread = new Thread(this::messageProcessing);

                // Set thread priorities for better timing
                receiveThread.setPriority(Thread.MAX_PRIORITY);
                processThread.setPriority(Thread.NORM_PRIORITY);

                receiveThread.start();
                processThread.start();

                // Wait for threads to finish
                receiveThread.join();
                processThread.join();

                System.out.println("\n<========== Simulation completed ========>");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error in Receiver application: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            closeConnection();
        }
    }

    /**
     * Connects to the simulator server
     * 
     * @return true if connection successful, false otherwise
     */
    private boolean connectToSimulator() {
        try {
            System.out.println("Connecting to simulator at " + SERVER_ADDRESS + ":" + SERVER_PORT + "...");
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

            // Configure socket for optimal performance
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(8192);

            out = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Print header once in console
            if (isFirstRun) {
                System.out.println(
                        "Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment");
            } else {
                System.out.println(
                        "Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment | ADAS Info");
            }

            // Send start signal to simulator
            out.println("START");
            out.flush();

            return true;
        } catch (IOException e) {
            System.out.println("Failed to connect to simulator: " + e.getMessage());
            return false;
        }
    }

    /**
     * Continuously receives messages from the socket and adds them to the queue
     * This method runs in a separate thread to ensure no messages are missed
     */
    private void socketReceive() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                if (message.equals("SIMULATION_COMPLETE")) {
                    System.out.println("\nReceived simulation complete signal from simulator.");

                    // Finalize the last segment if there is one
                    if (currentSegment != null) {
                        finalizeCurrentSegment();
                    }

                    running = false;
                    break;
                }

                // Add message to queue for processing
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

    /**
     * Processes messages from the queue and updates the console display
     * Also logs all received messages to a file
     * This method runs in a separate thread
     */
    private void messageProcessing() {
        // Create log file with buffered writer for better performance
        try (BufferedWriter logFile = new BufferedWriter(new FileWriter("simulation_log.txt"))) {
            // Write the header
            logFile.write("Message ID | Time Offset | Values | System Time Delta\n");
            logFile.write("---------------------------------------------------\n");

            // Process messages from the queue
            while (running || !messageQueue.isEmpty()) {
                try {
                    // Try to get a message from the queue with timeout
                    String message = messageQueue.poll(10, TimeUnit.MILLISECONDS);

                    if (message != null) {
                        // Calculate time delta with nanosecond precision
                        long currentTimeNanos = System.nanoTime();
                        long timeDeltaNanos = currentTimeNanos - simulationStartTime;
                        long timeDeltaMillis = TimeUnit.NANOSECONDS.toMillis(timeDeltaNanos);

                        // Process message and update values
                        processMessage(message, timeDeltaMillis, logFile);

                        // Perform segment detection and data extraction
                        detectSegment();

                        // Update ADAS info for the HMI
                        updateADASInfo();

                        // Update console display
                        updateConsoleDisplay();
                    }
                } catch (InterruptedException e) {
                    System.out.println("\nMessage processing interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            System.out.println("\nIssue encountered to Write in log file: " + e.getMessage());
        }
    }

    /**
     * Updates the ADAS information for the HMI
     */
    private void updateADASInfo() {
        // Update vehicle data in the HMI
        if (adasInterface != null) {
            adasInterface.updateVehicleData(
                    vehicleSpeed,
                    steeringAngle,
                    yawRate,
                    latAccel,
                    longAccel,
                    lastSegmentType,
                    currentSimTime);

            // Update ADAS warnings if we have saved segment data
            if (!isFirstRun && curveWarningAssist != null && currentGPS != null) {
                // Get the ADAS warning message
                String adasMessage = curveWarningAssist.update(currentGPS, currentSimTime);

                // Get the current upcoming segment and distance
                if (curveWarningAssist.hasUpcomingSegment()) {
                    SegmentData upcomingSegment = curveWarningAssist.getUpcomingSegment();
                    double distance = curveWarningAssist.getDistanceToUpcomingSegment();

                    // Update the HMI with this information
                    adasInterface.updateADASWarning(upcomingSegment, distance);
                } else {
                    // No upcoming segment
                    adasInterface.updateADASWarning(null, Double.MAX_VALUE);
                }
            }
        }
    }

    /**
     * Processes a received message and updates sensor values
     * Also logs messages to file with system time delta
     * 
     * @param message   The message to process
     * @param timeDelta System time delta since simulation start
     * @param logFile   FileWriter for logging
     */
    private void processMessage(String message, long timeDelta, Writer logFile) throws IOException {
        // Split the message into parts
        String[] parts = message.split("\\|");

        // Process message based on type
        if (parts.length < 2)
            return; // Skip invalid messages

        if (parts[0].trim().equals("CAN")) {
            if (parts.length < 4)
                return; // Skip invalid CAN messages

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
                            // Keep previous value if parsing fails
                        }
                    }
                    break;
                case "SPEED":
                    if (parts.length > 4) {
                        vehicleSpeedStr = extractValue(parts[4], "Speed=", " km/h");
                        try {
                            vehicleSpeed = Double.parseDouble(vehicleSpeedStr);
                        } catch (NumberFormatException e) {
                            // Keep previous value if parsing fails
                        }
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
                            // Keep previous values if parsing fails
                        }
                    }
                    break;
            }
        } else if (parts[0].trim().equals("GPS")) {
            if (parts.length < 4)
                return; // Skip invalid GPS messages

            double timeOffset = Double.parseDouble(parts[1]);
            currentSimTime = timeOffset;
            gpsLatitudeStr = parts[2];
            gpsLongitudeStr = parts[3];

            try {
                double latitude = Double.parseDouble(gpsLatitudeStr);
                double longitude = Double.parseDouble(gpsLongitudeStr);

                // Create new GPS coordinate
                GPScoordinates newGPS = new GPScoordinates(latitude, longitude, timeOffset);

                // Update current GPS
                currentGPS = newGPS;

                // Add to GPS buffer for heading calculation
                gpsBuffer.add(newGPS);
                if (gpsBuffer.size() > 2) {
                    gpsBuffer.remove(0); // Keep only the 2 most recent coordinates
                }

                // Calculate heading if we have at least 2 GPS points
                if (gpsBuffer.size() >= 2) {
                    GPScoordinates prev = gpsBuffer.get(0);
                    GPScoordinates curr = gpsBuffer.get(1);
                    lastHeading = SegmentCollection.calculateHeading(prev, curr);
                }
            } catch (NumberFormatException e) {
                // Keep previous values if parsing fails
            }
        }

        // Log the raw message with time delta
        logFile.write(message + " | " + timeDelta + "ms\n");
        logFile.flush(); // Ensure data is written immediately
    }

    private void detectSegment() {
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
    private void finalizeCurrentSegment() {
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

    /**
     * Extracts a value from a string containing key-value pairs
     * 
     * @param input  Input string
     * @param prefix The prefix to search for
     * @param suffix The suffix to remove
     * @return The extracted value
     */
    private String extractValue(String input, String prefix, String suffix) {
        int start = input.indexOf(prefix);
        if (start == -1)
            return "-";

        start += prefix.length();
        int end = input.indexOf(suffix, start);
        if (end == -1)
            return "-";

        // Format to single decimal place for consistent display
        try {
            double value = Double.parseDouble(input.substring(start, end).trim());
            return df.format(value);
        } catch (NumberFormatException e) {
            return input.substring(start, end).trim();
        }
    }

    /**
     * Updates the console display with the latest sensor values
     * Uses carriage return to update in place without creating new lines
     */
    private void updateConsoleDisplay() {
        // Format values for display with exact precision
        // Use DecimalFormat for time to preserve the decimal point
        DecimalFormat timeFormat = new DecimalFormat("#,##0.0");
        String timeStr = timeFormat.format(currentSimTime) + " ms";

        // Format all decimal values consistently with one decimal place
        String speedStr = vehicleSpeedStr.equals("-") ? "-" : vehicleSpeedStr + " km/h";
        String steerStr = steeringAngleStr.equals("-") ? "-" : steeringAngleStr + " deg";

        // Fix yaw rate display - ensure consistent formatting
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

        // Fix acceleration displays - ensure consistent formatting
        String latStr;
        if (latAccelStr.equals("-")) {
            latStr = "-";
        } else {
            try {
                latStr = df.format(Double.parseDouble(latAccelStr)) + " m/s²";
            } catch (NumberFormatException e) {
                latStr = latAccelStr + " m/s²";
            }
        }

        String longStr;
        if (longAccelStr.equals("-")) {
            longStr = "-";
        } else {
            try {
                longStr = df.format(Double.parseDouble(longAccelStr)) + " m/s²";
            } catch (NumberFormatException e) {
                longStr = longAccelStr + " m/s²";
            }
        }

        // Fix GPS display - show both coordinates when available
        String gpsStr;
        if (gpsLatitudeStr.equals("-") || gpsLongitudeStr.equals("-")) {
            gpsStr = "-";
        } else {
            try {
                // Format GPS coordinates with 6 decimal places for precision
                DecimalFormat gpsFormat = new DecimalFormat("0.000000");
                gpsStr = gpsFormat.format(Double.parseDouble(gpsLatitudeStr)) + ", " +
                        gpsFormat.format(Double.parseDouble(gpsLongitudeStr));
            } catch (NumberFormatException e) {
                gpsStr = gpsLatitudeStr + ", " + gpsLongitudeStr;
            }
        }

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
                    timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr, segmentStr);
        } else {
            displayLine = String.format("\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %-25s | %-8s | %s",
                    timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr, segmentStr, adasInfo);
        }

        // Print the line with carriage return to update in place
        System.out.print(displayLine);
        System.out.flush();
    }

    /**
     * Closes the socket connection
     */
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