package org.automotive;

import java.io.*;
import java.net.*;

import java.util.concurrent.*;
import java.text.DecimalFormat;

public class Receiver {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 54000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean running = false;
    private long simulationStartTime;

    // Data structures to store the latest values for each sensor
    private String steeringAngle = "-";
    private String vehicleSpeed = "-";
    private String yawRate = "-";
    private String longAccel = "-";
    private String latAccel = "-";
    private String gpsLatitude = "-";
    private String gpsLongitude = "-";
    private double currentSimTime = 0.0;

    // Formatter for decimal values
    private DecimalFormat df = new DecimalFormat("0.00");

    // Create a thread-safe queue for message processing
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    /**
     * Main method to run the Receiver application
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        Receiver receiver = new Receiver();
        receiver.start();
    }

    /**
     * Starts the Receiver application
     */
    public void start() {
        // Show welcome message and wait for user input to start
        System.out.println("=== CAN Trace Simulation Receiver ===");
        System.out.println("As Recieved an User input ./gradle run+Enter the simulation has started...");

        try {
            // Wait for user to press Enter
            new BufferedReader(new InputStreamReader(System.in)).readLine();

            // Connect to simulator
            if (connectToSimulator()) {
                running = true;
                simulationStartTime = System.currentTimeMillis();

                // Start threads for receiving and processing messages
                Thread receiveThread = new Thread(this::socketReceive);
                Thread processThread = new Thread(this::messageProcessing);

                receiveThread.start();
                processThread.start();

                // Wait for threads to finish
                receiveThread.join();
                processThread.join();

                System.out.println("\nSimulation completed. Press ENTER to exit.");
                new BufferedReader(new InputStreamReader(System.in)).readLine();
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
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("START");

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
                    running = false;
                    break;
                }

                // Add message to queue for processing
                messageQueue.put(message);
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("\nError reading from socket: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.out.println("\nMessage queue interrupted: " + e.getMessage());
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
        // Create log file
        try (FileWriter logFile = new FileWriter("simulation_log.txt")) {
            // Write the header exactly as shown in the example
            logFile.write(
                    "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long\n");

            // Print header once in console
            System.out.println(
                    "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long");

            // Process messages from the queue
            while (running || !messageQueue.isEmpty()) {
                try {
                    // Try to get a message from the queue with timeout
                    String message = messageQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (message != null) {
                        // Calculate time delta
                        long currentTime = System.currentTimeMillis();
                        long timeDelta = currentTime - simulationStartTime;

                        // Process message and update values
                        processMessage(message, timeDelta, logFile);

                        // Update console display
                        updateConsoleDisplay();
                    }
                } catch (InterruptedException e) {
                    System.out.println("\nMessage processing interrupted: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("\n Issue encountered to Write in log file: " + e.getMessage());
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
    private void processMessage(String message, long timeDelta, FileWriter logFile) throws IOException {
        // Split the message into parts
        String[] parts = message.split("\\|");

        // Process message based on type
        if (parts.length < 2)
            return; // Skip invalid messages

        if (parts[0].equals("CAN")) {
            if (parts.length < 4)
                return; // Skip invalid CAN messages

            String id = parts[1];
            double timeOffset = Double.parseDouble(parts[2]);
            currentSimTime = timeOffset;

            String type = parts[3];
            switch (type) {
                case "STEERING":
                    if (parts.length > 4) {
                        steeringAngle = extractValue(parts[4], "Angle=", "°");
                    }
                    break;
                case "SPEED":
                    if (parts.length > 4) {
                        vehicleSpeed = extractValue(parts[4], "Speed=", " km/h");
                    }
                    break;
                case "DYNAMICS":
                    if (parts.length > 6) {
                        yawRate = parts[4];
                        longAccel = parts[5];
                        latAccel = parts[6];
                    }
                    break;
            }
        } else if (parts[0].equals("GPS")) {
            if (parts.length < 4)
                return; // Skip invalid GPS messages

            double timeOffset = Double.parseDouble(parts[1]);
            currentSimTime = timeOffset;
            gpsLatitude = parts[2];
            gpsLongitude = parts[3];
        }

        // Log the raw message with time delta
        logFile.write(message + " | " + timeDelta + "ms\n");
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

        return input.substring(start, end);
    }

    /**
     * Updates the console display with the latest sensor values
     * Uses carriage return to update in place without creating new lines
     */
    private void updateConsoleDisplay() {
        // Format values for display
        String timeStr = String.format("%,d ms", (long) currentSimTime);
        String speedStr = vehicleSpeed.equals("-") ? "-" : vehicleSpeed + " km/h";
        String steerStr = steeringAngle.equals("-") ? "-" : steeringAngle + " deg";
        String yawStr = yawRate.equals("-") ? "-" : yawRate + "°/s";
        String latStr = latAccel.equals("-") ? "-" : latAccel + " m/s²";
        String longStr = longAccel.equals("-") ? "-" : longAccel + " m/s²";
        String gpsStr = (gpsLatitude.equals("-") || gpsLongitude.equals("-")) ? "-" : gpsLatitude + ", " + gpsLongitude;

        // Create the display line with proper spacing
        String displayLine = String.format("\r%-12s | %-13s | %-10s | %-7s | %-8s | %-9s | %s",
                timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr);

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
            if (socket != null)
                socket.close();

            System.out.println("Connection closed");
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}