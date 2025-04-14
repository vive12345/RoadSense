// this class extends CANFrame and represents a frame with vehicle dynamics data
public class VehicleDynamicsFrame extends CANFrame {
    // define variables for lateral acceleration, longitudinal acceleration, and yaw rate
    private double latAccel;
    private double longAccel;
    private double yawRate;

    // constructor to initialize the vehicle dynamics frame with id, timestamp, and acceleration values
    public VehicleDynamicsFrame(String id, double timestamp, double latAccel, double longAccel, double yawRate) {
        super(id, timestamp); // call the constructor of the parent class (CANFrame)
        this.latAccel = latAccel; // set lateral acceleration
        this.longAccel = longAccel; // set longitudinal acceleration
        this.yawRate = yawRate; // set yaw rate
    }

    // getter method for lateral acceleration
    public double getLatAccel() {
        return latAccel;
    }

    // getter method for longitudinal acceleration
    public double getLongAccel() {
        return longAccel;
    }

    // getter method for yaw rate
    public double getYawRate() {
        return yawRate;
    }

    // override toString method to return a string representation of the vehicle dynamics frame
    @Override
    public String toString() {
        return "VehicleDynamicsFrame [ID=" + id + ", Timestamp=" + timestamp +
               ", Lat Accel=" + latAccel + 
               ", Long Accel=" + longAccel + 
               ", Yaw Rate=" + yawRate + "]";
    }
}
