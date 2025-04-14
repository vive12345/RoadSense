// this is the base class for all types of can frames
abstract class CANFrame {
    // id of the can frame
    protected String id;  
    
    // time when the frame was captured
    protected double timestamp;

    // constructor to set id and timestamp
    public CANFrame(String id, double timestamp) { 
        this.id = id;
        this.timestamp = timestamp;
    }

    // method to get the frame id
    public String getId() { 
        return id; 
    }  

    // method to get the timestamp
    public double getTimestamp() { 
        return timestamp; 
    }

    // every child class must define how to print itself
    public abstract String toString();
}
