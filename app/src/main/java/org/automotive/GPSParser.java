package org.automotive;

import java.io.*;
import java.util.*; // ununsed remove it after testing

public class GPSParser {

    // Parse the GPS trace file and return a GPSTrace object
    public static GPSTrace parseGPSTraceFile(String filePath) throws IOException {
        GPSTrace trace = new GPSTrace();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));

        String line;
        int lineCount = 0;

        while ((line = reader.readLine()) != null) {
            line = line.trim();

            // Skip empty lines
            if (line.isEmpty()) {
                continue;
            }

            try {
                // Split by comma and handle semicolon
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    double latitude = Double.parseDouble(parts[0].trim());

                    // Remove any trailing semicolon from longitude
                    String longitudeStr = parts[1].trim();
                    if (longitudeStr.endsWith(";")) {
                        longitudeStr = longitudeStr.substring(0, longitudeStr.length() - 1);
                    }
                    double longitude = Double.parseDouble(longitudeStr);

                    // Calculate time offset - GPS coordinates are reported every second (1000ms)
                    double timeOffset = lineCount * 1000.0; // Convert to milliseconds

                    GPScoordinates coordinate = new GPScoordinates(latitude, longitude, timeOffset);
                    trace.addCoordinate(coordinate);

                    lineCount++;
                }
            } catch (NumberFormatException e) {
                System.err.println("Error parsing line: " + line);
                System.err.println("Error message: " + e.getMessage());
            }
        }

        reader.close();
        return trace;
    }

    // For testing the parser
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java GPSParser <gps_file_path>");
            return;
        }

        try {
            GPSTrace trace = parseGPSTraceFile(args[0]);
            System.out.println("Parsed " + trace.size() + " GPS coordinates");

            // Print the first 5 coordinates as a test
            for (int i = 0; i < Math.min(5, trace.size()); i++) {
                System.out.println(trace.getCoordinateAt(i));
            }

        } catch (IOException e) {
            System.out.println("Error reading GPS file: " + e.getMessage());
        }
    }
}