package org.automotive;

// this class extends CANFrame and represents a frame with vehicle speed data
class VehicleSpeedFrame extends CANFrame {
    // define a variable for speed
    private double speed;

    // constructor to initialize the vehicle speed frame with id, timestamp, and
    // speed value
    public VehicleSpeedFrame(String id, double timestamp, double rawValue) {
        super(id, timestamp); // call the constructor of the parent class (CANFrame)
        this.speed = rawValue; // set the speed value
    }

    // getter method for speed
    public double getSpeed() {
        return speed;
    }

    // override toString method to return a string representation of the vehicle
    // speed frame
    @Override
    public String toString() {
        return "VehicleSpeedFrame [ID=" + id + ", Time=" + timestamp + ", Speed=" + speed + " km/h]";
    }
}
