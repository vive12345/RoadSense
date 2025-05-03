package org.automotive;

import java.text.DecimalFormat;

/**
 * Basic Receiver implementation that displays real-time sensor data
 * from the CAN trace simulation.
 */
public class Receiver extends ReceiverBase {

    /**
     * Main method to run the Receiver application
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        Receiver receiver = new Receiver();
        receiver.runMultipleSimulations();
    }

    @Override
    protected void showWelcomeMessage() {
        System.out.println("<==== Simulation Receiver Started ====>");
        System.out.println("Press ENTER to connect to simulator and start the simulation...");
    }

    @Override
    protected void printConsoleHeader() {
        System.out.println(
                "Current Time | Vehicle Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS Lat/Long");
    }

    @Override
    protected void updateConsoleDisplay() {
        String[] formattedValues = formatDisplayValues();

        // Create the display line with proper spacing
        String displayLine = String.format("\r%-14s | %-13s | %-10s | %-9s | %-10s | %-11s | %s",
                formattedValues[0], formattedValues[1], formattedValues[2],
                formattedValues[3], formattedValues[4], formattedValues[5],
                formattedValues[6]);

        // Print the line with carriage return to update in place
        System.out.print(displayLine);
        System.out.flush();
    }
}