package org.automotive;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class CANTraceSimulation {
    private CANTrace canTrace;
    private GPSTrace gpsTrace;
    private ServerSocket serverSocket;
    private boolean running = false;
    private static final int PORT = 54000; // later instead of hardcoded we can get it from user

    // Constructor to initialize with both traces
    public CANTraceSimulation(CANTrace canTrace, GPSTrace gpsTrace) {
        this.canTrace = canTrace;
        this.gpsTrace = gpsTrace;
    }

    /**
     * Starts the simulation as a socket server that sends sensor values to clients
     * with the correct timing.
     * The simulation only starts if both CANTrace and GPSTrace objects have valid
     * data.
     */
    public void startSimulation() {
        // Check if both CANTrace and GPSTrace objects exist and have data
        if (canTrace == null || gpsTrace == null) {
            System.out.println("Error: CANTrace or GPSTrace object is missing. Cannot start simulation.");
            return;
        }

        // Check if objects contain data
        CANFrame firstFrame = canTrace.getNextMessage();
        canTrace.resetNextMessage(); // Reset so we don't lose the first message

        if (firstFrame == null || gpsTrace.size() == 0) {
            System.out.println("Error: CANTrace or GPSTrace contains no data. Cannot start simulation.");
            return;
        }

        System.out.println("Starting socket server on port " + PORT + "...");
        running = true;

        try {
            // Create server socket and listen for connections
            serverSocket = new ServerSocket(PORT);

            // Keep server running to accept multiple clients
            while (running) {
                try {
                    System.out.println("Waiting for client connection...");
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    // Handle client connection in a separate thread
                    handleClientConnection(clientSocket);

                } catch (IOException e) {
                    if (running) {
                        System.out.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error creating server socket: " + e.getMessage());
        } finally {
            // Clean up resources
            stopSimulation();
        }
    }

    /**
     * Handles client connection and simulation data streaming
     * 
     * @param clientSocket The connected client socket
     */
    private void handleClientConnection(Socket clientSocket) {
        Thread clientThread = new Thread(() -> {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
                // Wait for client to signal ready to receive data
                String inputLine = in.readLine();
                if (inputLine != null && inputLine.equals("START")) {
                    System.out.println("Client ready to receive data. Starting simulation...");

                    // Run the simulation and send data in real-time
                    runSimulation(out);

                    // Send simulation complete message
                    out.println("SIMULATION_COMPLETE");
                    System.out.println("====>>> Simulation completed successfully  <<<======");
                }

            } catch (IOException e) {
                System.out.println("Client disconnected: " + e.getMessage());
            } finally {
                try {
                    // Close the client socket when done
                    clientSocket.close();
                    System.out.println("Client connection closed");

                    // Reset the traces for next client
                    canTrace.resetNextMessage();
                    gpsTrace.resetNextCoordinate();
                } catch (IOException e) {
                    System.out.println("Error closing client socket: " + e.getMessage());
                }
            }
        });

        // Start the client handling thread
        clientThread.start();
    }

    /**
     * Runs the simulation, sending sensor data with correct timing
     * 
     * @param out PrintWriter to send data to client
     */
    /**
     * Runs the simulation, sending sensor data with correct timing
     * 
     * @param out PrintWriter to send data to client
     */
    private void runSimulation(PrintWriter out) {
        // Reset traces to start from beginning
        canTrace.resetNextMessage();
        gpsTrace.resetNextCoordinate();

        // Store the simulation start time - changed from long to double
        double startTime = System.currentTimeMillis();
        double startTimedummy = System.currentTimeMillis(); // Added missing variable
        System.out.println("Simulation started at: " + startTime);

        // Track the last sent time offset
        double lastTimeOffset = 0;

        // Send the first GPS coordinate (offset 0)
        GPScoordinates currentGPS = gpsTrace.getNextCoordinate();
        if (currentGPS != null) {
            sendGPSData(out, currentGPS);
            lastTimeOffset = currentGPS.getTimeOffset();
        }

        // Get the next GPS coordinate
        GPScoordinates nextGPS = gpsTrace.getNextCoordinate();
        double nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;

        // Process all CAN frames in time order
        CANFrame currentFrame;
        while ((currentFrame = canTrace.getNextMessage()) != null) {
            double frameOffset = currentFrame.getTimestamp();

            // Check if we need to send a GPS coordinate before this CAN frame
            while (nextGPS != null && nextGPSTime <= frameOffset) {
                // Calculate time to wait - changed to double
                double waitTime = calculateWaitTime(startTime, lastTimeOffset, nextGPSTime);
                if (waitTime > 0) {
                    try {
                        // Sleep with more precision using TimeUnit
                        long millisPart = (long) waitTime;
                        int nanosPart = (int) ((waitTime - millisPart) * 1_000_000);

                        TimeUnit.MILLISECONDS.sleep(millisPart);
                        TimeUnit.NANOSECONDS.sleep(nanosPart);
                    } catch (InterruptedException e) {
                        System.out.println("Simulation interrupted: " + e.getMessage());
                        return;
                    }
                }

                // Send GPS data
                sendGPSData(out, nextGPS);
                lastTimeOffset = nextGPSTime;

                // Get next GPS coordinate
                currentGPS = nextGPS;
                nextGPS = gpsTrace.getNextCoordinate();
                nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
            }

            // Calculate time to wait before sending current CAN frame - changed to double
            double waitTime = calculateWaitTime(startTime, lastTimeOffset, frameOffset);
            if (waitTime > 0) {
                try {
                    // Sleep with more precision using TimeUnit
                    long millisPart = (long) waitTime;
                    int nanosPart = (int) ((waitTime - millisPart) * 1_000_000);

                    TimeUnit.MILLISECONDS.sleep(millisPart);
                    TimeUnit.NANOSECONDS.sleep(nanosPart);
                } catch (InterruptedException e) {
                    System.out.println("Simulation interrupted: " + e.getMessage());
                    return;
                }
            }

            // Send CAN frame data
            sendCANData(out, currentFrame);
            lastTimeOffset = frameOffset;
        }

        // Send any remaining GPS coordinates
        while (nextGPS != null) {
            // Calculate time to wait - changed to double
            double waitTime = calculateWaitTime(startTime, lastTimeOffset, nextGPSTime);
            if (waitTime > 0) {
                try {
                    // Sleep with more precision using TimeUnit
                    long millisPart = (long) waitTime;
                    int nanosPart = (int) ((waitTime - millisPart) * 1_000_000);

                    TimeUnit.MILLISECONDS.sleep(millisPart);
                    TimeUnit.NANOSECONDS.sleep(nanosPart);
                } catch (InterruptedException e) {
                    System.out.println("Simulation interrupted: " + e.getMessage());
                    return;
                }
            }

            // Send GPS data
            sendGPSData(out, nextGPS);
            lastTimeOffset = nextGPSTime;

            // Get next GPS coordinate
            nextGPS = gpsTrace.getNextCoordinate();
            nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
        }

        // Calculate and print total simulation time - changed to double
        double endTime = System.currentTimeMillis();
        double totalDuration = endTime - startTime; // Using consistent variables
        System.out.println("Simulation ended at: " + endTime);
        System.out.println("Total simulation duration: " + totalDuration + " ms");
        System.out.println("Last time offset: " + lastTimeOffset + " ms");
    }

    /**
     * Calculates the wait time before sending the next message
     * Updated method signature to use double consistently
     * 
     * @param startTime     The simulation start time in ms
     * @param lastOffset    The time offset of the last sent message in ms
     * @param currentOffset The time offset of the current message to send in ms
     * @return The time to wait in ms (as double for more precise sleeping)
     */
    private double calculateWaitTime(double startTime, double lastOffset, double currentOffset) {
        double currentTime = System.currentTimeMillis();
        double elapsedTime = currentTime - startTime;
        double targetTime = currentOffset;

        return Math.max(0, targetTime - elapsedTime);
    }

    /**
     * Sends CAN frame data to the client
     * 
     * @param out   PrintWriter to send data
     * @param frame The CAN frame to send
     */
    private void sendCANData(PrintWriter out, CANFrame frame) {
        StringBuilder message = new StringBuilder();

        // Format: CAN|ID|TIMESTAMP|VALUES
        message.append("CAN|");
        message.append(frame.getId()).append("|");
        message.append(frame.getTimestamp()).append("|");

        // Add specific values based on frame type
        if (frame instanceof SteeringWheelAngleFrame) {
            SteeringWheelAngleFrame steeringFrame = (SteeringWheelAngleFrame) frame;
            message.append("STEERING|").append(steeringFrame.toString());
        } else if (frame instanceof VehicleSpeedFrame) {
            VehicleSpeedFrame speedFrame = (VehicleSpeedFrame) frame;
            message.append("SPEED|").append(speedFrame.toString());
        } else if (frame instanceof VehicleDynamicsFrame) {
            VehicleDynamicsFrame dynamicsFrame = (VehicleDynamicsFrame) frame;
            message.append("DYNAMICS|")
                    .append(dynamicsFrame.getYawRate()).append("|")
                    .append(dynamicsFrame.getLongAccel()).append("|")
                    .append(dynamicsFrame.getLatAccel());
        }

        // Send message to client
        out.println(message.toString());
    }

    /**
     * Sends GPS coordinate data to the client
     * 
     * @param out        PrintWriter to send data
     * @param coordinate The GPS coordinate to send
     */
    private void sendGPSData(PrintWriter out, GPScoordinates coordinate) {
        // Format: GPS|TIMESTAMP|LATITUDE|LONGITUDE
        String message = String.format("[ GPS ]|%.1f|%f|%f",
                coordinate.getTimeOffset(),
                coordinate.getLatitude(),
                coordinate.getLongitude());

        // Send message to client
        out.println(message);
    }

    /**
     * Stops the simulation and cleans up resources
     */
    public void stopSimulation() {
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("Server socket closed");
            } catch (IOException e) {
                System.out.println("Error closing server socket: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Ensure the user provides file paths as arguments
        if (args.length < 2) {
            System.out.println("Usage: java CANSimulation <can_file_path> <gps_file_path>");
            return;
        }

        String canFilePath = args[0];
        String gpsFilePath = args[1];

        try {
            // Parse CAN and GPS trace files
            System.out.println("Parsing CAN trace file: " + canFilePath);
            CANTrace canTrace = CANTraceParser.parseCANTraceFile(canFilePath);

            System.out.println("Parsing GPS trace file: " + gpsFilePath);
            GPSTrace gpsTrace = GPSParser.parseGPSTraceFile(gpsFilePath);

            // Create simulation object with both traces
            CANTraceSimulation simulation = new CANTraceSimulation(canTrace, gpsTrace);

            // Start the simulation server
            simulation.startSimulation();

        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
// package org.automotive;

// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.concurrent.TimeUnit;

// public class CANTraceSimulation {
// private CANTrace canTrace;
// private GPSTrace gpsTrace;
// private ServerSocket serverSocket;
// private boolean running = false;
// private static final int PORT = 54000;

// // Set TCP_NODELAY to true to minimize network latency
// private static final boolean TCP_NODELAY = true;

// // Constructor to initialize with both traces
// public CANTraceSimulation(CANTrace canTrace, GPSTrace gpsTrace) {
// this.canTrace = canTrace;
// this.gpsTrace = gpsTrace;
// }

// /**
// * Starts the simulation as a socket server that sends sensor values to
// clients
// * with the correct timing.
// * The simulation only starts if both CANTrace and GPSTrace objects have valid
// * data.
// */
// public void startSimulation() {
// // Check if both CANTrace and GPSTrace objects exist and have data
// if (canTrace == null || gpsTrace == null) {
// System.out.println("Error: CANTrace or GPSTrace object is missing. Cannot
// start simulation.");
// return;
// }

// // Check if objects contain data
// CANFrame firstFrame = canTrace.getNextMessage();
// canTrace.resetNextMessage(); // Reset so we don't lose the first message

// if (firstFrame == null || gpsTrace.size() == 0) {
// System.out.println("Error: CANTrace or GPSTrace contains no data. Cannot
// start simulation.");
// return;
// }

// System.out.println("Starting socket server on port " + PORT + "...");
// running = true;

// try {
// // Create server socket and listen for connections
// serverSocket = new ServerSocket(PORT);

// // Keep server running to accept multiple clients
// while (running) {
// try {
// System.out.println("Waiting for client connection...");
// Socket clientSocket = serverSocket.accept();

// // Configure socket for optimal performance
// clientSocket.setTcpNoDelay(TCP_NODELAY);
// clientSocket.setSendBufferSize(8192); // Larger buffer for better performance

// System.out.println("Client connected: " + clientSocket.getInetAddress());

// // Handle client connection in a separate thread
// handleClientConnection(clientSocket);

// } catch (IOException e) {
// if (running) {
// System.out.println("Error accepting client connection: " + e.getMessage());
// }
// }
// }
// } catch (IOException e) {
// System.out.println("Error creating server socket: " + e.getMessage());
// } finally {
// // Clean up resources
// stopSimulation();
// }
// }

// /**
// * Handles client connection and simulation data streaming
// *
// * @param clientSocket The connected client socket
// */
// private void handleClientConnection(Socket clientSocket) {
// Thread clientThread = new Thread(() -> {
// try (
// BufferedReader in = new BufferedReader(new
// InputStreamReader(clientSocket.getInputStream()));
// PrintWriter out = new PrintWriter(new
// BufferedOutputStream(clientSocket.getOutputStream()), true)) {
// // Wait for client to signal ready to receive data
// String inputLine = in.readLine();
// if (inputLine != null && inputLine.equals("START")) {
// System.out.println("Client ready to receive data. Starting simulation...");

// // Run the simulation and send data in real-time
// runSimulation(out);

// // Send simulation complete message
// out.println("SIMULATION_COMPLETE");
// System.out.println("Simulation completed successfully");
// }

// } catch (IOException e) {
// System.out.println("Client disconnected: " + e.getMessage());
// } finally {
// try {
// // Close the client socket when done
// clientSocket.close();
// System.out.println("Client connection closed");

// // Reset the traces for next client
// canTrace.resetNextMessage();
// gpsTrace.resetNextCoordinate();
// } catch (IOException e) {
// System.out.println("Error closing client socket: " + e.getMessage());
// }
// }
// });

// // Set higher priority for timing precision
// clientThread.setPriority(Thread.MAX_PRIORITY);

// // Start the client handling thread
// clientThread.start();
// }

// /**
// * Runs the simulation, sending sensor data with correct timing
// *
// * @param out PrintWriter to send data to client
// */
// private void runSimulation(PrintWriter out) {
// // Reset traces to start from beginning
// canTrace.resetNextMessage();
// gpsTrace.resetNextCoordinate();

// // Store the simulation start time in both nanoseconds (for precision) and
// // milliseconds (for logging)
// long startTimeNanos = System.nanoTime();
// long startTimeMillis = System.currentTimeMillis();
// System.out.println("Simulation started at: " + startTimeMillis);

// // Track the last sent time offset
// double lastTimeOffset = 0;

// // Send the first GPS coordinate (offset 0)
// GPScoordinates currentGPS = gpsTrace.getNextCoordinate();
// if (currentGPS != null) {
// sendGPSData(out, currentGPS);
// lastTimeOffset = currentGPS.getTimeOffset();
// }

// // Get the next GPS coordinate
// GPScoordinates nextGPS = gpsTrace.getNextCoordinate();
// double nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() :
// Double.MAX_VALUE;

// // Process all CAN frames in time order
// CANFrame currentFrame;
// while ((currentFrame = canTrace.getNextMessage()) != null) {
// double frameOffset = currentFrame.getTimestamp();

// // Check if we need to send a GPS coordinate before this CAN frame
// while (nextGPS != null && nextGPSTime <= frameOffset) {
// // Calculate time to wait
// long waitTime = calculateWaitTime(startTimeNanos, nextGPSTime);
// if (waitTime > 0) {
// precisionSleep(waitTime);
// }

// // Send GPS data
// sendGPSData(out, nextGPS);
// lastTimeOffset = nextGPSTime;

// // Get next GPS coordinate
// currentGPS = nextGPS;
// nextGPS = gpsTrace.getNextCoordinate();
// nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
// }

// // Calculate time to wait before sending current CAN frame
// long waitTime = calculateWaitTime(startTimeNanos, frameOffset);
// if (waitTime > 0) {
// precisionSleep(waitTime);
// }

// // Send CAN frame data
// sendCANData(out, currentFrame);
// lastTimeOffset = frameOffset;

// // Force flush output after each message for real-time delivery
// out.flush();
// }

// // Send any remaining GPS coordinates
// while (nextGPS != null) {
// // Calculate time to wait
// long waitTime = calculateWaitTime(startTimeNanos, nextGPSTime);
// if (waitTime > 0) {
// precisionSleep(waitTime);
// }

// // Send GPS data
// sendGPSData(out, nextGPS);
// lastTimeOffset = nextGPSTime;

// // Get next GPS coordinate
// nextGPS = gpsTrace.getNextCoordinate();
// nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
// }

// // Calculate and print total simulation time
// long endTimeMillis = System.currentTimeMillis();
// long totalDuration = endTimeMillis - startTimeMillis;
// System.out.println("Simulation ended at: " + endTimeMillis);
// System.out.println("Total simulation duration: " + totalDuration + " ms");
// System.out.println("Last time offset: " + lastTimeOffset + " ms");
// }

// /**
// * More precise wait implementation that uses a hybrid approach:
// * - Thread.sleep for most of the wait time
// * - Busy-wait for final precision
// *
// * @param waitTimeMillis The total time to wait in milliseconds
// */
// private void precisionSleep(long waitTimeMillis) {
// if (waitTimeMillis <= 0) {
// return;
// }

// final long PRECISION_THRESHOLD_MS = 10; // Threshold below which we use busy
// waiting

// try {
// // If wait time is longer than threshold, use sleep for most of it
// if (waitTimeMillis > PRECISION_THRESHOLD_MS) {
// Thread.sleep(waitTimeMillis - PRECISION_THRESHOLD_MS);
// }

// // Use busy-waiting for the final milliseconds to get precise timing
// long startTime = System.nanoTime();
// long waitTimeNanos = (waitTimeMillis <= PRECISION_THRESHOLD_MS ?
// waitTimeMillis : PRECISION_THRESHOLD_MS)
// * 1_000_000;

// while (System.nanoTime() - startTime < waitTimeNanos) {
// // Busy wait for precision
// Thread.yield(); // Allow other threads to run, but keep checking time
// }
// } catch (InterruptedException e) {
// System.out.println("Sleep interrupted: " + e.getMessage());
// Thread.currentThread().interrupt();
// }
// }

// /**
// * Calculates the wait time before sending the next message with improved
// * precision
// *
// * @param startTimeNanos The simulation start time in nanoseconds
// * @param targetOffset The time offset of the current message to send in ms
// * @return The time to wait in ms
// */
// private long calculateWaitTime(long startTimeNanos, double targetOffset) {
// // Calculate elapsed time with nanosecond precision
// long elapsedNanos = System.nanoTime() - startTimeNanos;
// long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

// // Round target offset to nearest millisecond to match the precision we can
// // achieve
// long targetMillis = Math.round(targetOffset);

// // Calculate wait time - never return negative values
// return Math.max(0, targetMillis - elapsedMillis);
// }

// /**
// * Sends CAN frame data to the client
// *
// * @param out PrintWriter to send data
// * @param frame The CAN frame to send
// */
// private void sendCANData(PrintWriter out, CANFrame frame) {
// StringBuilder message = new StringBuilder();

// // Format: CAN|ID|TIMESTAMP|VALUES
// message.append("CAN|");
// message.append(frame.getId()).append("|");
// message.append(String.format("%.1f", frame.getTimestamp())).append("|");

// // Add specific values based on frame type
// if (frame instanceof SteeringWheelAngleFrame) {
// SteeringWheelAngleFrame steeringFrame = (SteeringWheelAngleFrame) frame;
// message.append("STEERING|").append(steeringFrame.toString());
// } else if (frame instanceof VehicleSpeedFrame) {
// VehicleSpeedFrame speedFrame = (VehicleSpeedFrame) frame;
// message.append("SPEED|").append(speedFrame.toString());
// } else if (frame instanceof VehicleDynamicsFrame) {
// VehicleDynamicsFrame dynamicsFrame = (VehicleDynamicsFrame) frame;
// message.append("DYNAMICS|")
// .append(dynamicsFrame.getYawRate()).append("|")
// .append(dynamicsFrame.getLongAccel()).append("|")
// .append(dynamicsFrame.getLatAccel());
// }

// // Send message to client
// out.println(message.toString());
// }

// /**
// * Sends GPS coordinate data to the client
// *
// * @param out PrintWriter to send data
// * @param coordinate The GPS coordinate to send
// */
// private void sendGPSData(PrintWriter out, GPScoordinates coordinate) {
// // Format: GPS|TIMESTAMP|LATITUDE|LONGITUDE with consistent formatting
// String message = String.format("GPS|%.1f|%.6f|%.6f",
// coordinate.getTimeOffset(),
// coordinate.getLatitude(),
// coordinate.getLongitude());

// // Send message to client
// out.println(message);
// }

// /**
// * Stops the simulation and cleans up resources
// */
// public void stopSimulation() {
// running = false;

// if (serverSocket != null && !serverSocket.isClosed()) {
// try {
// serverSocket.close();
// System.out.println("Server socket closed");
// } catch (IOException e) {
// System.out.println("Error closing server socket: " + e.getMessage());
// }
// }
// }

// public static void main(String[] args) {
// // Ensure the user provides file paths as arguments
// if (args.length < 2) {
// System.out.println("Usage: java CANSimulation <can_file_path>
// <gps_file_path>");
// return;
// }

// String canFilePath = args[0];
// String gpsFilePath = args[1];

// try {
// // Parse CAN and GPS trace files
// System.out.println("Parsing CAN trace file: " + canFilePath);
// CANTrace canTrace = CANTraceParser.parseCANTraceFile(canFilePath);

// System.out.println("Parsing GPS trace file: " + gpsFilePath);
// GPSTrace gpsTrace = GPSParser.parseGPSTraceFile(gpsFilePath);

// // Create simulation object with both traces
// CANTraceSimulation simulation = new CANTraceSimulation(canTrace, gpsTrace);

// // Start the simulation server
// simulation.startSimulation();

// } catch (IOException e) {
// System.out.println("Error reading file: " + e.getMessage());
// e.printStackTrace();
// }
// }
// }