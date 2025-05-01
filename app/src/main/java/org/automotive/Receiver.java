// package org.automotive;

// import java.io.*;
// import java.net.*;

// import java.util.concurrent.*;

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

//     // Create a thread-safe queue for message processing
//     private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

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
//         System.out.println("As Recieved an User input ./gradle run+Enter the simulation has started...");

//         try {
//             // Wait for user to press Enter
//             new BufferedReader(new InputStreamReader(System.in)).readLine();

//             // Connect to simulator
//             if (connectToSimulator()) {
//                 running = true;
//                 simulationStartTime = System.currentTimeMillis();

//                 // Start threads for receiving and processing messages
//                 Thread receiveThread = new Thread(this::socketReceive);
//                 Thread processThread = new Thread(this::messageProcessing);

//                 receiveThread.start();
//                 processThread.start();

//                 // Wait for threads to finish
//                 receiveThread.join();
//                 processThread.join();

//                 System.out.println("\n <========== Simulation completed ======>");
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
//             out = new PrintWriter(socket.getOutputStream(), true);
//             in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

//             out.println("START");

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
//                 messageQueue.put(message);
//             }
//         } catch (IOException e) {
//             if (running) {
//                 System.out.println("\nError reading from socket: " + e.getMessage());
//             }
//         } catch (InterruptedException e) {
//             System.out.println("\nMessage queue interrupted: " + e.getMessage());
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
//         // Create log file
//         try (FileWriter logFile = new FileWriter("simulation_log.txt")) {
//             // Write the header exactly as shown in the example
//             logFile.write(
//                     "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long\n");

//             // Print header once in console
//             System.out.println(
//                     "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long");

//             // Process messages from the queue
//             while (running || !messageQueue.isEmpty()) {
//                 try {
//                     // Try to get a message from the queue with timeout
//                     String message = messageQueue.poll(100, TimeUnit.MILLISECONDS);

//                     if (message != null) {
//                         // Calculate delta=when each msg received-when client requested simulation start
//                         double currentTime = System.currentTimeMillis();
//                         double timeDelta = currentTime - simulationStartTime;

//                         // Process message and update values
//                         processMessage(message, timeDelta, logFile);

//                         // Update console display
//                         updateConsoleDisplay();
//                     }
//                 } catch (InterruptedException e) {
//                     System.out.println("\nMessage processing interrupted: " + e.getMessage());
//                 }
//             }
//         } catch (IOException e) {
//             System.out.println("\n Issue encountered to Write in log file: " + e.getMessage());
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
//     private void processMessage(String message, double timeDelta, FileWriter logFile) throws IOException {
//         // Split the message into parts
//         String[] parts = message.split("\\|");

//         // Process message based on type
//         if (parts.length < 2)
//             return; // Skip invalid messages

//         if (parts[0].equals("CAN")) {
//             if (parts.length < 4)
//                 return; // Skip invalid CAN messages

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
//         } else if (parts[0].equals("GPS")) {
//             if (parts.length < 4)
//                 return; // Skip invalid GPS messages

//             double timeOffset = Double.parseDouble(parts[1]);
//             currentSimTime = timeOffset;
//             gpsLatitude = parts[2];
//             gpsLongitude = parts[3];
//         }

//         // Log the raw message with time delta
//         logFile.write(message + " | " + timeDelta + "ms\n");
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

//         return input.substring(start, end);
//     }

//     /**
//      * Updates the console display with the latest sensor values
//      * Uses carriage return to update in place without creating new lines
//      */
//     private void updateConsoleDisplay() {
//         // Format values for display
//         String timeStr = String.format("%,d ms", (long) currentSimTime);
//         String speedStr = vehicleSpeed.equals("-") ? "-" : vehicleSpeed + " km/h";
//         String steerStr = steeringAngle.equals("-") ? "-" : steeringAngle + " deg";
//         String yawStr = yawRate.equals("-") ? "-" : yawRate + "°/s";
//         String latStr = latAccel.equals("-") ? "-" : latAccel + " m/s²";
//         String longStr = longAccel.equals("-") ? "-" : longAccel + " m/s²";
//         String gpsStr = (gpsLatitude.equals("-") || gpsLongitude.equals("-")) ? "-" : gpsLatitude + ", " + gpsLongitude;

//         // Create the display line with proper spacing
//         String displayLine = String.format("\r%-12s | %-13s | %-10s | %-7s | %-8s | %-9s | %s",
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


    private final SegmentManager segmentManager = new SegmentManager();
    private final SegmentDetector segmentDetector = new SegmentDetector();

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
        System.out.println("<==== Simulation Receiver Started ====>");

        while(true) {//Reconnect loop
            System.out.println("Press ENTER to connect to simulator and start the simulation...");

            try {
                // Wait for user to press Enter
                new BufferedReader(new InputStreamReader(System.in)).readLine();
                System.out.println("As Recieved an User input ./gradle run+Enter the simulation has started...");

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

                    System.out.println("\n <========== Simulation completed ======>");
                    System.out.println("Press ENTER to exit.");
                    new BufferedReader(new InputStreamReader(System.in)).readLine();
                }
            } catch (IOException | InterruptedException e) {
                System.out.println("Error in Receiver application: " + e.getMessage());
                e.printStackTrace();
                break; //Exit
            }
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
                    segmentManager.finalizeSegment();
                    segmentManager.printAll();  // prints all segment summaries
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
        if (parts.length < 2) return; // Skip invalid messages

        if (parts[0].trim().equals("CAN")) {
            if (parts.length < 4) return;

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
            if (parts.length < 4) return;

            double timeOffset = Double.parseDouble(parts[1]);
            currentSimTime = timeOffset;
            gpsLatitude = parts[2];
            gpsLongitude = parts[3];
        }

        // After updating fields, compute values for segment logic
        double speed = vehicleSpeed.equals("-") ? 0.0 : Double.parseDouble(vehicleSpeed);
        double yaw = yawRate.equals("-") ? 0.0 : Double.parseDouble(yawRate);
        double longA = longAccel.equals("-") ? 0.0 : Double.parseDouble(longAccel);
        double latA = latAccel.equals("-") ? 0.0 : Double.parseDouble(latAccel);
        GPScoordinates gpsCoord = (gpsLatitude.equals("-") || gpsLongitude.equals("-")) ? null
                : new GPScoordinates(Double.parseDouble(gpsLatitude), Double.parseDouble(gpsLongitude), currentSimTime);

        if (!yawRate.equals("-")) {
            segmentDetector.addYawValue(Double.parseDouble(yawRate));
        }

        if (gpsCoord != null) {
            SegmentType currentType = segmentDetector.getCurrentSegment();
            segmentManager.update(currentType, gpsCoord, speed, yaw, longA, latA);
        }

        // Log raw message with system time delta
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
        String yawStr = yawRate.equals("-") ? "-"
                : (yawRate.equals("-") ? "-" : df.format(Double.parseDouble(yawRate)) + "°/s");
        String latStr = latAccel.equals("-") ? "-"
                : (latAccel.equals("-") ? "-" : df.format(Double.parseDouble(latAccel)) + " m/s²");
        String longStr = longAccel.equals("-") ? "-"
                : (longAccel.equals("-") ? "-" : df.format(Double.parseDouble(longAccel)) + " m/s²");
        String gpsStr = (gpsLatitude.equals("-") || gpsLongitude.equals("-")) ? "-"
                : df.format(Double.parseDouble(gpsLatitude)) + ", " +
                        df.format(Double.parseDouble(gpsLongitude));

        // Create the display line with proper spacing
        String displayLine = String.format("\r%-14s | %-13s | %-10s | %-7s | %-8s | %-9s | %s",
                timeStr, speedStr, steerStr, yawStr, latStr, longStr, gpsStr);

        // Print the line with carriage return to update in place
        System.out.print(displayLine + "                              "); // padding to overwrite leftovers

        System.out.flush();

        // Curve Warning Assist (Part 3)
        if (!gpsLatitude.equals("-") && !gpsLongitude.equals("-")) {
            GPScoordinates current = new GPScoordinates(
                    Double.parseDouble(gpsLatitude),
                    Double.parseDouble(gpsLongitude),
                    currentSimTime
            );

            SegmentData upcoming = segmentManager.findUpcomingSegment(current, 50.0);
            if (upcoming != null) {
                double distance = SegmentData.haversine(
                        current.getLatitude(), current.getLongitude(),
                        upcoming.startGPS.getLatitude(), upcoming.startGPS.getLongitude());

                String warning = String.format("→ Upcoming segment in %.1f m | %s",
                        distance, upcoming.type);

                if (upcoming.type == SegmentType.CURVE) {
                    double delta = (upcoming.endHeading - upcoming.startHeading + 540) % 360 - 180;
                    String turnDirection = delta > 5 ? "Right" : (delta < -5 ? "Left" : "Straight");
                    warning += " | Direction: " + upcoming.getCurveDirection();


                }

                System.out.println("\n" + warning);
            }
        }

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