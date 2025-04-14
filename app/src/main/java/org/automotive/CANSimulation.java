package org.automotive;

import java.io.IOException;

public class CANSimulation {
    public static void main(String[] args) {
        // Ensure the user provides a file path as an argument
        if (args.length < 1) {
            System.out.println("Usage: java CANSimulation <file_path>");
            return;
        }

        String filePath = args[0]; // Get the file path from the command line

        try {
            CANTrace trace = CANTraceParser.parseCANTraceFile(filePath);

            // Print first 30 messages
            for (int i = 0; i < 30; i++) {
                System.out.println(trace.getNextMessage());
            }

            // Reset and print next 5 messages
            trace.resetNextMessage();
            for (int i = 0; i < 5; i++) {
                System.out.println(trace.getNextMessage());
            }

        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }
}
