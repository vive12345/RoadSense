package org.automotive;

import java.util.*;

public class GPSTrace {
    // list to store all gps coordinate
    private List<GPSCoordinates> coordinates;
    // index to keep track of the next message
    private int currentIndex = 0;

    public GPSTrace() {
        this.coordinates = new ArrayList<>();
    }

    // Add a GPS coordinate to the trace
    public void addCoordinate(GPSCoordinates coordinate) {
        coordinates.add(coordinate);
    }

    // Get all coordinates
    public List<GPSCoordinates> getAllCoordinates() {
        return coordinates;
    }

    // Get the next coordinate in the trace
    public GPSCoordinates getNextCoordinate() {
        if (currentIndex >= coordinates.size())
            return null;
        return coordinates.get(currentIndex++);
    }

    // Reset the index to start from the beginning
    public void resetNextCoordinate() {
        currentIndex = 0;
        System.out.println("Reset next coordinate index.");
    }

    // Get the number of coordinates in the trace
    public int size() {
        return coordinates.size();
    }

    // Get a coordinate at a specific index - we can actually remove it as well no
    // need
    public GPSCoordinates getCoordinateAt(int index) {
        if (index >= 0 && index < coordinates.size()) {
            return coordinates.get(index);
        }
        return null;
    }
}