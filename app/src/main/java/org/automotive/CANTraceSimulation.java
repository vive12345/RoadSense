// package org.automotive;

// import java.io.*;
// import java.net.*;
// import java.util.concurrent.TimeUnit;

// public class CANTraceSimulation {
//     private CANTrace canTrace;
//     private GPSTrace gpsTrace;
//     private ServerSocket serverSocket;
//     private boolean running = false;
//     private static final int PORT = 54000; // later instead of hardcoded we can get it from user

//     // Constructor to initialize with both traces
//     public CANTraceSimulation(CANTrace canTrace, GPSTrace gpsTrace) {
//         this.canTrace = canTrace;
//         this.gpsTrace = gpsTrace;
//     }

//     /**
//      * Starts the simulation as a socket server that sends sensor values to clients
//      * with the correct timing.
//      * The simulation only starts if both CANTrace and GPSTrace objects have valid
//      * data.
//      */
//     public void startSimulation() {
//         // Check if both CANTrace and GPSTrace objects exist and have data
//         if (canTrace == null || gpsTrace == null) {
//             System.out.println("Error: CANTrace or GPSTrace object is missing. Cannot start simulation.");
//             return;
//         }

//         // Check if objects contain data
//         CANFrame firstFrame = canTrace.getNextMessage();
//         canTrace.resetNextMessage(); // Reset so we don't lose the first message

//         if (firstFrame == null || gpsTrace.size() == 0) {
//             System.out.println("Error: CANTrace or GPSTrace contains no data. Cannot start simulation.");
//             return;
//         }

//         System.out.println("Starting socket server on port " + PORT + "...");
//         running = true;

//         try {
//             // Create server socket and listen for connections
//             serverSocket = new ServerSocket(PORT);

//             // Keep server running to accept multiple clients
//             while (running) {
//                 try {
//                     System.out.println("Waiting for client connection...");
//                     Socket clientSocket = serverSocket.accept();
//                     System.out.println("Client connected: " + clientSocket.getInetAddress());

//                     // Handle client connection in a separate thread
//                     handleClientConnection(clientSocket);

//                 } catch (IOException e) {
//                     if (running) {
//                         System.out.println("Error accepting client connection: " + e.getMessage());
//                     }
//                 }
//             }
//         } catch (IOException e) {
//             System.out.println("Error creating server socket: " + e.getMessage());
//         } finally {
//             // Clean up resources
//             stopSimulation();
//         }
//     }

//     /**
//      * Handles client connection and simulation data streaming
//      * 
//      * @param clientSocket The connected client socket
//      */
//     private void handleClientConnection(Socket clientSocket) {
//         Thread clientThread = new Thread(() -> {
//             try (
//                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
//                 // Wait for client to signal ready to receive data
//                 String inputLine = in.readLine();
//                 if (inputLine != null && inputLine.equals("START")) {
//                     System.out.println("Client ready to receive data. Starting simulation...");

//                     // Run the simulation and send data in real-time
//                     runSimulation(out);

//                     // Send simulation complete message
//                     out.println("SIMULATION_COMPLETE");
//                     System.out.println("====>>> Simulation completed successfully  <<<======");
//                 }

//             } catch (IOException e) {
//                 System.out.println("Client disconnected: " + e.getMessage());
//             } finally {
//                 try {
//                     // Close the client socket when done
//                     clientSocket.close();
//                     System.out.println("Client connection closed");

//                     // Reset the traces for next client
//                     canTrace.resetNextMessage();
//                     gpsTrace.resetNextCoordinate();
//                 } catch (IOException e) {
//                     System.out.println("Error closing client socket: " + e.getMessage());
//                 }
//             }
//         });

//         // Start the client handling thread
//         clientThread.start();
//     }

//     /**
//      * Runs the simulation, sending sensor data with correct timing
//      * 
//      * @param out PrintWriter to send data to client
//      */
//     private void runSimulation(PrintWriter out) {
//         // Reset traces to start from beginning
//         canTrace.resetNextMessage();
//         gpsTrace.resetNextCoordinate();

//         // Store the simulation start time - changed from long to double
//         double startTime = System.nanoTime();

//         System.out.println("Simulation started at: " + startTime);

//         // Track the last sent time offset
//         double lastTimeOffset = 0;

//         // Send the first GPS coordinate (offset 0)
//         GPScoordinates currentGPS = gpsTrace.getNextCoordinate();
//         if (currentGPS != null) {
//             sendGPSData(out, currentGPS);
//             lastTimeOffset = currentGPS.getTimeOffset();
//         }

//         // Get the next GPS coordinate
//         GPScoordinates nextGPS = gpsTrace.getNextCoordinate();
//         double nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;

//         // Process all CAN frames in time order
//         CANFrame currentFrame;
//         while ((currentFrame = canTrace.getNextMessage()) != null) {
//             double frameOffset = currentFrame.getTimestamp();

//             // Check if we need to send a GPS coordinate before this CAN frame
//             while (nextGPS != null && nextGPSTime <= frameOffset) {
//                 // Calculate time to wait - changed to double
//                 double waitTime = calculateWaitTime(startTime, lastTimeOffset, nextGPSTime);
//                 if (waitTime > 0) {
//                     try {
//                         // Sleep with more precision using TimeUnit
//                         long millisPart = (long) waitTime;
//                         int nanosPart = (int) ((waitTime - millisPart) * 1_000_000);

//                         TimeUnit.MILLISECONDS.sleep(millisPart);
//                         TimeUnit.NANOSECONDS.sleep(nanosPart);
//                     } catch (InterruptedException e) {
//                         System.out.println("Simulation interrupted: " + e.getMessage());
//                         return;
//                     }
//                 }

//                 // Send GPS data
//                 sendGPSData(out, nextGPS);
//                 lastTimeOffset = nextGPSTime;

//                 // Get next GPS coordinate
//                 currentGPS = nextGPS;
//                 nextGPS = gpsTrace.getNextCoordinate();
//                 nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
//             }

//             // Calculate time to wait before sending current CAN frame - changed to double
//             double waitTime = calculateWaitTime(startTime, lastTimeOffset, frameOffset);
//             if (waitTime > 0) {
//                 try {
//                     // Sleep with more precision using TimeUnit
//                     long millisPart = (long) waitTime;
//                     int nanosPart = (int) ((waitTime - millisPart) * 1_000_000);

//                     TimeUnit.MILLISECONDS.sleep(millisPart);
//                     TimeUnit.NANOSECONDS.sleep(nanosPart);
//                 } catch (InterruptedException e) {
//                     System.out.println("Simulation interrupted: " + e.getMessage());
//                     return;
//                 }
//             }

//             // Send CAN frame data
//             sendCANData(out, currentFrame);
//             lastTimeOffset = frameOffset;
//         }

//         // Send any remaining GPS coordinates
//         while (nextGPS != null) {
//             // Calculate time to wait - changed to double
//             double waitTime = calculateWaitTime(startTime, lastTimeOffset, nextGPSTime);
//             if (waitTime > 0) {
//                 try {
//                     // Sleep with more precision using TimeUnit
//                     long millisPart = (long) waitTime;
//                     int nanosPart = (int) ((waitTime - millisPart) * 1_000_000);

//                     TimeUnit.MILLISECONDS.sleep(millisPart);
//                     TimeUnit.NANOSECONDS.sleep(nanosPart);
//                 } catch (InterruptedException e) {
//                     System.out.println("Simulation interrupted: " + e.getMessage());
//                     return;
//                 }
//             }

//             // Send GPS data
//             sendGPSData(out, nextGPS);
//             lastTimeOffset = nextGPSTime;

//             // Get next GPS coordinate
//             nextGPS = gpsTrace.getNextCoordinate();
//             nextGPSTime = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
//         }

//         // Calculate and print total simulation time - changed to double
//         double endTime = System.nanoTime();
//         double totalDuration = endTime - startTime; // Using consistent variables
//         System.out.println("Simulation ended at: " + endTime);
//         System.out.println("Total simulation duration: " + totalDuration + " ms");
//         System.out.println("Last time offset: " + lastTimeOffset + " ms");
//     }

//     /**
//      * Calculates the wait time before sending the next message
//      * Updated method signature to use double consistently
//      * 
//      * @param startTime     The simulation start time in ms
//      * @param lastOffset    The time offset of the last sent message in ms
//      * @param currentOffset The time offset of the current message to send in ms
//      * @return The time to wait in ms (as double for more precise sleeping)
//      */
//     private double calculateWaitTime(double startTime, double lastOffset, double currentOffset) {
//         double currentTime = System.nanoTime();
//         double elapsedTime = currentTime - startTime;
//         double targetTime = currentOffset;

//         return Math.max(0, targetTime - elapsedTime);
//     }

//     /**
//      * Sends CAN frame data to the client
//      * 
//      * @param out   PrintWriter to send data
//      * @param frame The CAN frame to send
//      */
//     private void sendCANData(PrintWriter out, CANFrame frame) {
//         StringBuilder message = new StringBuilder();

//         // Format: CAN|ID|TIMESTAMP|VALUES
//         message.append("CAN|");
//         message.append(frame.getId()).append("|");
//         message.append(frame.getTimestamp()).append("|");

//         // Add specific values based on frame type
//         if (frame instanceof SteeringWheelAngleFrame) {
//             SteeringWheelAngleFrame steeringFrame = (SteeringWheelAngleFrame) frame;
//             message.append("STEERING|").append(steeringFrame.toString());
//         } else if (frame instanceof VehicleSpeedFrame) {
//             VehicleSpeedFrame speedFrame = (VehicleSpeedFrame) frame;
//             message.append("SPEED|").append(speedFrame.toString());
//         } else if (frame instanceof VehicleDynamicsFrame) {
//             VehicleDynamicsFrame dynamicsFrame = (VehicleDynamicsFrame) frame;
//             message.append("DYNAMICS|")
//                     .append(dynamicsFrame.getYawRate()).append("|")
//                     .append(dynamicsFrame.getLongAccel()).append("|")
//                     .append(dynamicsFrame.getLatAccel());
//         }

//         // Send message to client
//         out.println(message.toString());
//     }

//     /**
//      * Sends GPS coordinate data to the client
//      * 
//      * @param out        PrintWriter to send data
//      * @param coordinate The GPS coordinate to send
//      */
//     private void sendGPSData(PrintWriter out, GPScoordinates coordinate) {
//         // Format: GPS|TIMESTAMP|LATITUDE|LONGITUDE
//         String message = String.format("GPS|%.1f|%f|%f",
//                 coordinate.getTimeOffset(),
//                 coordinate.getLatitude(),
//                 coordinate.getLongitude());

//         // Send message to client
//         out.println(message);
//     }

//     /**
//      * Stops the simulation and cleans up resources
//      */
//     public void stopSimulation() {
//         running = false;

//         if (serverSocket != null && !serverSocket.isClosed()) {
//             try {
//                 serverSocket.close();
//                 System.out.println("Server socket closed");
//             } catch (IOException e) {
//                 System.out.println("Error closing server socket: " + e.getMessage());
//             }
//         }
//     }

//     public static void main(String[] args) {
//         // Ensure the user provides file paths as arguments
//         if (args.length < 2) {
//             System.out.println("Usage: java CANSimulation <can_file_path> <gps_file_path>");
//             return;
//         }

//         String canFilePath = args[0];
//         String gpsFilePath = args[1];

//         try {
//             // Parse CAN and GPS trace files
//             System.out.println("Parsing CAN trace file: " + canFilePath);
//             CANTrace canTrace = CANTraceParser.parseCANTraceFile(canFilePath);

//             System.out.println("Parsing GPS trace file: " + gpsFilePath);
//             GPSTrace gpsTrace = GPSParser.parseGPSTraceFile(gpsFilePath);

//             // Create simulation object with both traces
//             CANTraceSimulation simulation = new CANTraceSimulation(canTrace, gpsTrace);

//             // Start the simulation server
//             simulation.startSimulation();

//         } catch (IOException e) {
//             System.out.println("Error reading file: " + e.getMessage());
//             e.printStackTrace();
//         }
//     }
// }
package org.automotive;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class CANTraceSimulation {
    private CANTrace canTrace;
    private GPSTrace gpsTrace;
    private ServerSocket serverSocket;
    private boolean running = false;
    private static final int PORT = 54000;

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
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
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
    private void runSimulation(PrintWriter out) {
        // Reset traces to start from beginning
        canTrace.resetNextMessage();
        gpsTrace.resetNextCoordinate();

        // Store the simulation start time in nanoseconds for high precision
        long simStartTimeNanos = System.nanoTime();
        System.out.println("Simulation started at: " + simStartTimeNanos + " ns");

        // Send the first GPS coordinate (offset 0)
        GPScoordinates currentGPS = gpsTrace.getNextCoordinate();
        if (currentGPS != null) {
            sendGPSData(out, currentGPS);
        }

        // Get the next GPS coordinate
        GPScoordinates nextGPS = gpsTrace.getNextCoordinate();
        double nextGPSTimeMs = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;

        // Variable to track simulation time in milliseconds
        double simTimeMs = 0;

        // Process all CAN frames in time order
        CANFrame currentFrame;
        while ((currentFrame = canTrace.getNextMessage()) != null) {
            double frameOffsetMs = currentFrame.getTimestamp();

            // Check if we need to send a GPS coordinate before this CAN frame
            while (nextGPS != null && nextGPSTimeMs <= frameOffsetMs) {
                // Calculate and wait for the correct time to send GPS data
                waitUntilSimulationTime(simStartTimeNanos, nextGPSTimeMs);

                // Send GPS data
                sendGPSData(out, nextGPS);

                // Update simulation time to match the sent GPS time
                simTimeMs = nextGPSTimeMs;

                // Get next GPS coordinate
                currentGPS = nextGPS;
                nextGPS = gpsTrace.getNextCoordinate();
                nextGPSTimeMs = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
            }

            // Calculate and wait for the correct time to send CAN frame
            waitUntilSimulationTime(simStartTimeNanos, frameOffsetMs);

            // Send CAN frame data
            sendCANData(out, currentFrame);
            // System.out.println("Sent CAN frame at offset: " + frameOffsetMs + " ms");

            // Update simulation time to match the sent frame time
            simTimeMs = frameOffsetMs;
        }

        // Send any remaining GPS coordinates
        while (nextGPS != null) {
            // Calculate and wait for the correct time to send GPS data
            waitUntilSimulationTime(simStartTimeNanos, nextGPSTimeMs);

            // Send GPS data
            sendGPSData(out, nextGPS);

            // Update simulation time
            simTimeMs = nextGPSTimeMs;

            // Get next GPS coordinate
            nextGPS = gpsTrace.getNextCoordinate();
            nextGPSTimeMs = (nextGPS != null) ? nextGPS.getTimeOffset() : Double.MAX_VALUE;
        }

        // Calculate and print total simulation time
        long simEndTimeNanos = System.nanoTime();
        double actualDurationMs = (simEndTimeNanos - simStartTimeNanos) / 1_000_000.0;

        System.out.println("Simulation ended at: " + simEndTimeNanos + " ns");
        System.out.println("Total simulation duration: " + actualDurationMs + " ms");
        System.out.println("Final simulation time: " + simTimeMs + " ms");
    }

    /**
     * Waits until the specified simulation time has been reached
     * 
     * @param startTimeNanos The simulation start time in nanoseconds
     * @param targetTimeMs   The target simulation time in milliseconds
     */
    private void waitUntilSimulationTime(long startTimeNanos, double targetTimeMs) {
        // Convert target time to nanoseconds
        long targetTimeNanos = startTimeNanos + (long) (targetTimeMs * 1_000_000);

        // Get current time
        long currentTimeNanos = System.nanoTime();

        // Calculate wait time in nanoseconds
        long waitTimeNanos = targetTimeNanos - currentTimeNanos;

        // Only wait if the target time is in the future
        if (waitTimeNanos > 0) {
            try {
                // Split into milliseconds and nanoseconds for more precise sleeping
                long waitTimeMs = waitTimeNanos / 1_000_000;
                int remainingNanos = (int) (waitTimeNanos % 1_000_000);

                // Sleep for the calculated time
                if (waitTimeMs > 0) {
                    TimeUnit.MILLISECONDS.sleep(waitTimeMs);
                }
                if (remainingNanos > 0) {
                    TimeUnit.NANOSECONDS.sleep(remainingNanos);
                }
            } catch (InterruptedException e) {
                System.out.println("Sleep interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        } else if (waitTimeNanos < -10_000_000) { // More than 10ms behind
            // Log warning if we're significantly behind schedule
            // System.out.println("Warning: Simulation lagging behind by " +
            // (-waitTimeNanos / 1_000_000.0) + " ms");
        }
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
        String message = String.format("GPS|%.1f|%f|%f",
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