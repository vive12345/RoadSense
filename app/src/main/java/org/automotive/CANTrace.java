import java.util.*;

// this class stores all can frames and helps to manage them
class CANTrace {
    // list to store all can frames
    private List<CANFrame> frames = new ArrayList<>();
    
    // index to keep track of the next message
    private int currentIndex = 0;

    // method to add a new frame to the list
    public void addFrame(CANFrame frame) {
        frames.add(frame);
    }

    // method to print all frames in the list
    public void printTrace() {
        for (CANFrame frame : frames) {
            System.out.println(frame);
        }
    }

    // method to get the next frame in the list
    public CANFrame getNextMessage() {
        if (currentIndex >= frames.size()) return null;
        return frames.get(currentIndex++);
    }

    // method to reset the index to start from the beginning
    public void resetNextMessage() {
        currentIndex = 0;
        System.out.println("Reset next message index.");
    }
}
