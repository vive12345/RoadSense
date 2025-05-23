package org.automotive;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.text.DecimalFormat;

/**
 * Abstract base class for all Receiver implementations.
 * Handles socket communication, message processing, and common display logic.
 */
public abstract class ReceiverBase {
    protected static final String SERVER_ADDRESS = "localhost";
    protected static final int SERVER_PORT = 54000;

    protected Socket socket;
    protected PrintWriter out;
    protected BufferedReader in;
    protected boolean running = false;
    protected double simulationStartTime;

    protected double steeringAngle = 0.0;
    protected double vehicleSpeed = 0.0;
    protected double yawRate = 0.0;
    protected double longAccel = 0.0;
    protected double latAccel = 0.0;
    protected GPScoordinates currentGPS = null;
    protected double currentSimTime = 0.0;

    protected String steeringAngleStr = "-";
    protected String vehicleSpeedStr = "-";
    protected String yawRateStr = "-";
    protected String longAccelStr = "-";
    protected String latAccelStr = "-";
    protected String gpsLatitudeStr = "-";
    protected String gpsLongitudeStr = "-";

    protected DecimalFormat df = new DecimalFormat("0.0");
    protected DecimalFormat df2 = new DecimalFormat("0.00");
    protected DecimalFormat gpsFormat = new DecimalFormat("0.000000");

    // Create a thread-safe queue for message processing
    protected BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1000);

    /**
     * Runs multiple simulations without restarting the application
     */
    public void runMultipleSimulations() {
        boolean continueRunning = true;
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (continueRunning) {

            resetSensorValues();

            initialize();

            // Start a new simulation
            start();

            afterSimulation();

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

    protected void resetSensorValues() {
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
    }

    protected void initialize() {

    }

    protected void afterSimulation() {

    }

    /**
     * Starts the Receiver application
     */
    public void start() {
        showWelcomeMessage();

        try {
            // Wait for user to press Enter
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            System.out.println("Starting simulation...");

            // Connect to simulator
            if (connectToSimulator()) {
                running = true;
                simulationStartTime = (double) System.nanoTime();

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

    protected abstract void showWelcomeMessage();

    protected abstract void printConsoleHeader();

    /**
     * Connects to the simulator server
     */
    protected boolean connectToSimulator() {
        try {
            System.out.println("Connecting to simulator at " + SERVER_ADDRESS + ":" + SERVER_PORT + "...");
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(8192);

            out = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            printConsoleHeader();
            out.println("START");
            out.flush();

            return true;
        } catch (IOException e) {
            System.out.println("Failed to connect to simulator: " + e.getMessage());
            return false;
        }
    }

    /**
     * Continuously receives messages from the socket and adds them to the queue -->
     * runs in a separate thread.
     */
    protected void socketReceive() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                if (message.equals("SIMULATION_COMPLETE")) {
                    System.out.println("\nReceived simulation complete signal from simulator.");

                    // Perform any cleanup operations before ending
                    beforeSimulationComplete();

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

    protected void beforeSimulationComplete() {
        // Default implementation does nothing
    }

    protected void messageProcessing() {
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
                        double currentTimeNanos = (double) System.nanoTime();
                        double timeDeltaNanos = currentTimeNanos - simulationStartTime;
                        double timeDeltaMillis = timeDeltaNanos / 1_000_000.0;

                        processMessage(message, timeDeltaMillis, logFile);

                        processAdditionalData();

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

    protected void processAdditionalData() {
        // Default implementation does nothing
    }

    /**
     * Processes a received message and updates sensor values
     */
    protected void processMessage(String message, double timeDelta, Writer logFile) throws IOException {
        // Split the message into parts
        String[] parts = message.split("\\|");

        // Process message based on type
        if (parts.length < 2)
            return; // Skip invalid messages

        if (parts[0].trim().equals("CAN")) {
            processCANMessage(parts);
        } else if (parts[0].trim().equals("GPS")) {
            processGPSMessage(parts);
        }
        logFile.write(message + " | " + String.format("%.6f", timeDelta) + "ms\n");
        logFile.flush(); // Ensure data is written immediately
    }

    // Process CAN message and update sensor values
    protected void processCANMessage(String[] parts) {
        if (parts.length < 4)
            return; // Skip invalid CAN messages

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
    }

    /**
     * Process GPS message and update GPS coordinates
     * 
     * @param parts Message parts
     */
    protected void processGPSMessage(String[] parts) {
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
            currentGPS = new GPScoordinates(latitude, longitude, timeOffset);
        } catch (NumberFormatException e) {
            // Keep previous values if parsing fails
        }
    }

    /**
     * Extracts a value from a string containing key-value pairs
     */
    protected String extractValue(String input, String prefix, String suffix) {
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

    protected abstract void updateConsoleDisplay();
    // Format sensor values for display

    protected String[] formatDisplayValues() {
        DecimalFormat timeFormat = new DecimalFormat("#,##0.0");
        String timeStr = timeFormat.format(currentSimTime) + " ms";

        String speedStr = vehicleSpeedStr.equals("-") ? "-" : vehicleSpeedStr + " km/h";
        String steerStr = steeringAngleStr.equals("-") ? "-" : steeringAngleStr + " deg";

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
        String gpsStr;
        if (gpsLatitudeStr.equals("-") || gpsLongitudeStr.equals("-")) {
            gpsStr = "-";
        } else {
            try {
                gpsStr = gpsFormat.format(Double.parseDouble(gpsLatitudeStr)) + ", " +
                        gpsFormat.format(Double.parseDouble(gpsLongitudeStr));
            } catch (NumberFormatException e) {
                gpsStr = gpsLatitudeStr + ", " + gpsLongitudeStr;
            }
        }

        return new String[] { timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr };
    }

    protected void closeConnection() {
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