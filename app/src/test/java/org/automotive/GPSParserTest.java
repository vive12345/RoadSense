package org.automotive;

import java.io.IOException;
import java.util.List;

public class GPSParserTest {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java org.automotive.GPSParserTest <gps_file_path>");
            return;
        }

        String gpsFilePath = args[0];

        try {
            System.out.println("Parsing GPS file: " + gpsFilePath);

            // Parse GPS file
            GPSTrace gpsTrace = GPSParser.parseGPSTraceFile(gpsFilePath);

            // Get all coordinates
            List<GPSCoordinates> coordinates = gpsTrace.getAllCoordinates();

            // Print total count
            System.out.println("Successfully parsed " + coordinates.size() + " GPS coordinates");

            // Print first 5 coordinates for verification
            System.out.println("\nFirst 5 coordinates:");
            for (int i = 0; i < Math.min(5, coordinates.size()); i++) {
                GPSCoordinates coord = coordinates.get(i);
                System.out.println("Coordinate " + i + ": " +
                        "Lat=" + coord.getLatitude() + ", " +
                        "Long=" + coord.getLongitude() + ", " +
                        "Time=" + coord.getTimeOffset() + "ms");
            }

            // Print last 5 coordinates for verification
            System.out.println("\nLast 5 coordinates:");
            for (int i = Math.max(0, coordinates.size() - 5); i < coordinates.size(); i++) {
                GPSCoordinates coord = coordinates.get(i);
                System.out.println("Coordinate " + i + ": " +
                        "Lat=" + coord.getLatitude() + ", " +
                        "Long=" + coord.getLongitude() + ", " +
                        "Time=" + coord.getTimeOffset() + "ms");
            }

            // Test iterator functionality
            System.out.println("\nTesting iterator (first 3 coordinates):");
            gpsTrace.resetNextCoordinate();
            for (int i = 0; i < 3; i++) {
                GPSCoordinates coord = gpsTrace.getNextCoordinate();
                if (coord != null) {
                    System.out.println("Next coordinate: " + coord);
                } else {
                    System.out.println("No more coordinates");
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading GPS file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}