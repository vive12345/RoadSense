package org.automotive;

// class for steering wheel angle frame, extends can frame
class SteeringWheelAngleFrame extends CANFrame {
    // variable to store the angle
    private double angle;

    // constructor to set id, timestamp, and angle value
    public SteeringWheelAngleFrame(String id, double timestamp, double rawValue) {
        super(id, timestamp);
        this.angle = rawValue;
    }

    // getter method for angle
    public double getAngle() {
        return angle;
    }

    // method to return the frame details as a string
    @Override
    public String toString() {
        return "SteeringWheelAngleFrame [ID=" + id + ", Time=" + timestamp + ", Angle=" + angle + "°]";
    }
}
