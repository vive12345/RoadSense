package org.automotive;

public class GPSCoordinates {
    private double latitude;
    private double longitude;
    private double timeOffset; // in milliseconds

    // Constructor which
    public GPSCoordinates(double latitude, double longitude, double timeOffset) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeOffset = timeOffset;
    }

    // Getters
    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getTimeOffset() {
        return timeOffset;
    }

    @Override // we can remove this when submitting the code it's for testing the values
    public String toString() {
        return "[" + latitude + "," + longitude + "," + timeOffset + "]";
    }
}