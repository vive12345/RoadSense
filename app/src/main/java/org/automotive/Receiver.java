// package org.automotive;

// import java.io.*;
// import java.net.*;
// import java.util.concurrent.*;
// import java.text.DecimalFormat;

// public class Receiver {
//     private static final String SERVER_ADDRESS = "localhost";
//     private static final int SERVER_PORT = 54000;

//     private Socket socket;
//     private PrintWriter out;
//     private BufferedReader in;
//     private boolean running = false;
//     private long simulationStartTime;

//     // Data structures to store the latest values for each sensor
//     private String steeringAngle = "-";
//     private String vehicleSpeed = "-";
//     private String yawRate = "-";
//     private String longAccel = "-";
//     private String latAccel = "-";
//     private String gpsLatitude = "-";
//     private String gpsLongitude = "-";
//     private double currentSimTime = 0.0;

//     // Formatter for decimal values - limit to 1 decimal place for display
//     private DecimalFormat df = new DecimalFormat("0.0");

//     // Create a thread-safe queue for message processing with larger capacity
//     private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1000);

//     /**
//      * Main method to run the Receiver application
//      * 
//      * @param args Command line arguments (not used)
//      */
//     public static void main(String[] args) {
//         Receiver receiver = new Receiver();
//         receiver.start();
//     }

//     /**
//      * Starts the Receiver application
//      */
//     public void start() {
//         // Show welcome message and wait for user input to start
//         System.out.println("<==== Simulation Receiver Started ====>");
//         System.out.println("Press ENTER to connect to simulator and start the simulation...");

//         try {
//             // Wait for user to press Enter
//             new BufferedReader(new InputStreamReader(System.in)).readLine();
//             System.out.println("As Recieved an User input ./gradle run+Enter the simulation has started...");

//             // Connect to simulator
//             if (connectToSimulator()) {
//                 running = true;
//                 simulationStartTime = System.nanoTime(); // Use nanoTime for precision

//                 // Start threads for receiving and processing messages
//                 Thread receiveThread = new Thread(this::socketReceive);
//                 Thread processThread = new Thread(this::messageProcessing);

//                 // Set thread priorities for better timing
//                 receiveThread.setPriority(Thread.MAX_PRIORITY);
//                 processThread.setPriority(Thread.NORM_PRIORITY);

//                 receiveThread.start();
//                 processThread.start();

//                 // Wait for threads to finish
//                 receiveThread.join();
//                 processThread.join();

//                 System.out.println("\n <========== Simulation completed ======>");
//                 System.out.println("Press ENTER to exit.");
//                 new BufferedReader(new InputStreamReader(System.in)).readLine();
//             }
//         } catch (IOException | InterruptedException e) {
//             System.out.println("Error in Receiver application: " + e.getMessage());
//             e.printStackTrace();
//         } finally {
//             // Clean up resources
//             closeConnection();
//         }
//     }

//     /**
//      * Connects to the simulator server
//      * 
//      * @return true if connection successful, false otherwise
//      */
//     private boolean connectToSimulator() {
//         try {
//             System.out.println("Connecting to simulator at " + SERVER_ADDRESS + ":" + SERVER_PORT + "...");
//             socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

//             // Configure socket for optimal performance
//             socket.setTcpNoDelay(true);
//             socket.setReceiveBufferSize(8192);

//             out = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), true);
//             in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

//             // Print header once in console
//             System.out.println(
//                     "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long");

//             // Send start signal to simulator
//             out.println("START");
//             out.flush();

//             return true;
//         } catch (IOException e) {
//             System.out.println("Failed to connect to simulator: " + e.getMessage());
//             return false;
//         }
//     }

//     /**
//      * Continuously receives messages from the socket and adds them to the queue
//      * This method runs in a separate thread to ensure no messages are missed
//      */
//     private void socketReceive() {
//         try {
//             String message;
//             while (running && (message = in.readLine()) != null) {
//                 if (message.equals("SIMULATION_COMPLETE")) {
//                     System.out.println("\nReceived simulation complete signal from simulator.");
//                     running = false;
//                     break;
//                 }

//                 // Add message to queue for processing
//                 boolean added = messageQueue.offer(message, 50, TimeUnit.MILLISECONDS);
//                 if (!added) {
//                     System.out.println("\nWarning: Message queue full, dropped message: " + message);
//                 }
//             }
//         } catch (IOException e) {
//             if (running) {
//                 System.out.println("\nError reading from socket: " + e.getMessage());
//             }
//         } catch (InterruptedException e) {
//             System.out.println("\nMessage queue interrupted: " + e.getMessage());
//             Thread.currentThread().interrupt();
//         } finally {
//             running = false;
//         }
//     }

//     /**
//      * Processes messages from the queue and updates the console display
//      * Also logs all received messages to a file
//      * This method runs in a separate thread
//      */
//     private void messageProcessing() {
//         // Create log file with buffered writer for better performance
//         try (BufferedWriter logFile = new BufferedWriter(new FileWriter("simulation_log.txt"))) {
//             // Write the header
//             logFile.write("Message ID | Time Offset | Values | System Time Delta\n");
//             logFile.write("---------------------------------------------------\n");

//             // Process messages from the queue
//             while (running || !messageQueue.isEmpty()) {
//                 try {
//                     // Try to get a message from the queue with timeout
//                     String message = messageQueue.poll(10, TimeUnit.MILLISECONDS);

//                     if (message != null) {
//                         // Calculate time delta with nanosecond precision
//                         long currentTimeNanos = System.nanoTime();
//                         long timeDeltaNanos = currentTimeNanos - simulationStartTime;
//                         long timeDeltaMillis = TimeUnit.NANOSECONDS.toMillis(timeDeltaNanos);

//                         // Process message and update values
//                         processMessage(message, timeDeltaMillis, logFile);

//                         // Update console display
//                         updateConsoleDisplay();
//                     }
//                 } catch (InterruptedException e) {
//                     System.out.println("\nMessage processing interrupted: " + e.getMessage());
//                     Thread.currentThread().interrupt();
//                 }
//             }
//         } catch (IOException e) {
//             System.out.println("\nIssue encountered to Write in log file: " + e.getMessage());
//         }
//     }

//     /**
//      * Processes a received message and updates sensor values
//      * Also logs messages to file with system time delta
//      * 
//      * @param message   The message to process
//      * @param timeDelta System time delta since simulation start
//      * @param logFile   FileWriter for logging
//      */
//     private void processMessage(String message, long timeDelta, Writer logFile) throws IOException {
//         // Split the message into parts
//         String[] parts = message.split("\\|");

//         // Process message based on type
//         if (parts.length < 2)
//             return; // Skip invalid messages

//         String messageType = parts[0].trim();

//         if (messageType.equals("CAN")) {
//             if (parts.length < 4)
//                 return; // Skip invalid CAN messages

//             String id = parts[1];
//             double timeOffset = Double.parseDouble(parts[2]);
//             currentSimTime = timeOffset;

//             String type = parts[3];
//             switch (type) {
//                 case "STEERING":
//                     if (parts.length > 4) {
//                         steeringAngle = extractValue(parts[4], "Angle=", "°");
//                     }
//                     break;
//                 case "SPEED":
//                     if (parts.length > 4) {
//                         vehicleSpeed = extractValue(parts[4], "Speed=", " km/h");
//                     }
//                     break;
//                 case "DYNAMICS":
//                     if (parts.length > 6) {
//                         yawRate = parts[4];
//                         longAccel = parts[5];
//                         latAccel = parts[6];
//                     }
//                     break;
//             }
//         } else if (messageType.equals("GPS")) {
//             if (parts.length < 4)
//                 return; // Skip invalid GPS messages

//             double timeOffset = Double.parseDouble(parts[1]);
//             currentSimTime = timeOffset;

//             // Fix: Directly store the raw GPS values without conversion attempts
//             gpsLatitude = parts[2].trim();
//             gpsLongitude = parts[3].trim();
//         }

//         // Log the raw message with time delta
//         logFile.write(message + " | " + timeDelta + "ms\n");
//         logFile.flush(); // Ensure data is written immediately
//     }

//     /**
//      * Extracts a value from a string containing key-value pairs
//      * 
//      * @param input  Input string
//      * @param prefix The prefix to search for
//      * @param suffix The suffix to remove
//      * @return The extracted value
//      */
//     private String extractValue(String input, String prefix, String suffix) {
//         int start = input.indexOf(prefix);
//         if (start == -1)
//             return "-";

//         start += prefix.length();
//         int end = input.indexOf(suffix, start);
//         if (end == -1)
//             return "-";

//         // Format to single decimal place for consistent display
//         try {
//             double value = Double.parseDouble(input.substring(start, end).trim());
//             return df.format(value);
//         } catch (NumberFormatException e) {
//             return input.substring(start, end).trim();
//         }
//     }

//     /**
//      * Updates the console display with the latest sensor values
//      * Uses carriage return to update in place without creating new lines
//      */
//     private void updateConsoleDisplay() {
//         // Format values for display with exact precision
//         // Use DecimalFormat for time to preserve the decimal point
//         DecimalFormat timeFormat = new DecimalFormat("#,##0.0");
//         String timeStr = timeFormat.format(currentSimTime) + " ms";

//         // Format all decimal values consistently with one decimal place
//         String speedStr = vehicleSpeed.equals("-") ? "-" : vehicleSpeed + " km/h";
//         String steerStr = steeringAngle.equals("-") ? "-" : steeringAngle + " deg";

//         // Format yaw rate with proper error handling
//         String yawStr = "-";
//         if (!yawRate.equals("-")) {
//             try {
//                 yawStr = df.format(Double.parseDouble(yawRate)) + "°/s";
//             } catch (NumberFormatException e) {
//                 yawStr = yawRate + "°/s";
//             }
//         }

//         // Format lateral acceleration with proper error handling
//         String latStr = "-";
//         if (!latAccel.equals("-")) {
//             try {
//                 latStr = df.format(Double.parseDouble(latAccel)) + " m/s²";
//             } catch (NumberFormatException e) {
//                 latStr = latAccel + " m/s²";
//             }
//         }

//         // Format longitudinal acceleration with proper error handling
//         String longStr = "-";
//         if (!longAccel.equals("-")) {
//             try {
//                 longStr = df.format(Double.parseDouble(longAccel)) + " m/s²";
//             } catch (NumberFormatException e) {
//                 longStr = longAccel + " m/s²";
//             }
//         }

//         // Fix for GPS coordinates display - no conversion or formatting
//         String gpsStr = (gpsLatitude.equals("-") || gpsLongitude.equals("-")) ? "-"
//                 : gpsLatitude + ", " + gpsLongitude;

//         // Create the display line with proper spacing
//         String displayLine = String.format("\r%-14s | %-13s | %-10s | %-10s | %-10s | %-10s | %s",
//                 timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr);

//         // Print the line with carriage return to update in place
//         System.out.print(displayLine);
//         System.out.flush();
//     }

//     /**
//      * Closes the socket connection
//      */
//     private void closeConnection() {
//         running = false;

//         try {
//             if (out != null)
//                 out.close();
//             if (in != null)
//                 in.close();
//             if (socket != null)
//                 socket.close();

//             System.out.println("Connection closed");
//         } catch (IOException e) {
//             System.out.println("Error closing connection: " + e.getMessage());
//         }
//     }
// }
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
        Receiver receiver = new Receiver();
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

            // Start a new simulation
            start();

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
     * Resets all sensor values to their default state
     */
    private void resetSensorValues() {
        steeringAngle = "-";
        vehicleSpeed = "-";
        yawRate = "-";
        longAccel = "-";
        latAccel = "-";
        gpsLatitude = "-";
        gpsLongitude = "-";
        currentSimTime = 0.0;
    }

    /**
     * Starts the Receiver application
     */
    public void start() {
        // Show welcome message and wait for user input to start
        System.out.println("<==== Simulation Receiver Started ====>");
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
            System.out.println(
                    "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long");

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
        } else if (parts[0].trim().equals("GPS")) {
            if (parts.length < 4)
                return; // Skip invalid GPS messages

            double timeOffset = Double.parseDouble(parts[1]);
            currentSimTime = timeOffset;
            gpsLatitude = parts[2];
            gpsLongitude = parts[3];
        }

        // Log the raw message with time delta
        logFile.write(message + " | " + timeDelta + "ms\n");
        logFile.flush(); // Ensure data is written immediately
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
        String speedStr = vehicleSpeed.equals("-") ? "-" : vehicleSpeed + " km/h";
        String steerStr = steeringAngle.equals("-") ? "-" : steeringAngle + " deg";

        // Fix yaw rate display - ensure consistent formatting
        String yawStr;
        if (yawRate.equals("-")) {
            yawStr = "-";
        } else {
            try {
                yawStr = df.format(Double.parseDouble(yawRate)) + "°/s";
            } catch (NumberFormatException e) {
                yawStr = yawRate + "°/s";
            }
        }

        // Fix acceleration displays - ensure consistent formatting
        String latStr;
        if (latAccel.equals("-")) {
            latStr = "-";
        } else {
            try {
                latStr = df.format(Double.parseDouble(latAccel)) + " m/s²";
            } catch (NumberFormatException e) {
                latStr = latAccel + " m/s²";
            }
        }

        String longStr;
        if (longAccel.equals("-")) {
            longStr = "-";
        } else {
            try {
                longStr = df.format(Double.parseDouble(longAccel)) + " m/s²";
            } catch (NumberFormatException e) {
                longStr = longAccel + " m/s²";
            }
        }

        // Fix GPS display - show both coordinates when available
        String gpsStr;
        if (gpsLatitude.equals("-") || gpsLongitude.equals("-")) {
            gpsStr = "-";
        } else {
            try {
                // Format GPS coordinates with 6 decimal places for precision
                DecimalFormat gpsFormat = new DecimalFormat("0.000000");
                gpsStr = gpsFormat.format(Double.parseDouble(gpsLatitude)) + ", " +
                        gpsFormat.format(Double.parseDouble(gpsLongitude));
            } catch (NumberFormatException e) {
                gpsStr = gpsLatitude + ", " + gpsLongitude;
            }
        }

        // Create the display line with proper spacing
        String displayLine = String.format("\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %s",
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
            if (socket != null && !socket.isClosed())
                socket.close();

            System.out.println("Connection closed");
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}