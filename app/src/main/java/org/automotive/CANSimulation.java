package org.automotive;

import java.io.IOException;

public class CANSimulation {
    private CANTrace canTrace;
    private GPSTrace gpsTrace;

    // Constructor to initialize with both traces
    public CANSimulation(CANTrace canTrace, GPSTrace gpsTrace) {
        this.canTrace = canTrace;
        this.gpsTrace = gpsTrace;
    }

    // Method to start simulation that checks if both traces have data
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

        System.out.println("Simulation starting with valid CAN and GPS data...");

        // Here you would implement the socket server and simulation logic
        // for the next parts of your project
    }

    public static void main(String[] args) {
        // Ensure the user provides a file path as an argument
        if (args.length < 2) {
            System.out.println("Usage: java CANSimulation <file_path>");
            return;
        }

        String canFilePath = args[0]; // Get the file path from the command line
        String gpsFilePath = args[1];

        try {
            CANTrace canTrace = CANTraceParser.parseCANTraceFile(canFilePath);
            GPSTrace gpsTrace = GPSParser.parseGPSTraceFile(gpsFilePath);
            // Create simulation object with both traces
            CANSimulation simulation = new CANSimulation(canTrace, gpsTrace);

            // Try to start the simulation
            simulation.startSimulation();

            // Print first 30 messages
            for (int i = 0; i < 30; i++) {
                System.out.println(canTrace.getNextMessage());
            }

            // Print first 30 messages from GPS
            for (int i = 0; i < 30; i++) {
                System.out.println(gpsTrace.getNextCoordinate());
            }

        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }
}
