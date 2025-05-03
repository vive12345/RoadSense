package org.automotive;

/**
 * ReceiverWithHMI extends the enhanced receiver to add a graphical HMI
 * for the ADAS Curve Warning System.
 */
public class ReceiverWithHMI extends ReceiverEnhanced {
    // HMI Interface
    private ADASInterface adasInterface;

    /**
     * Main method to run the Receiver application with HMI
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        ReceiverWithHMI receiver = new ReceiverWithHMI();
        receiver.runMultipleSimulations();
    }

    @Override
    protected void initialize() {
        super.initialize();

        // Initialize the HMI
        initializeHMI();
    }

    @Override
    protected void showWelcomeMessage() {
        System.out.println("<==== Simulation Receiver with HMI Started ====>");
        if (isFirstRun) {
            System.out.println("First run - segment data will be collected.");
        } else {
            System.out.println("Using segment data from previous run for ADAS Curve Warning System.");
        }
        System.out.println("Press ENTER to connect to simulator and start the simulation...");
    }

    /**
     * Initialize the HMI interface
     */
    private void initializeHMI() {
        // Create new ADAS Interface with current run status
        adasInterface = new ADASInterface(isFirstRun);
    }

    @Override
    protected void processAdditionalData() {
        // First perform segment detection (from parent class)
        super.processAdditionalData();

        // Then update ADAS info for the HMI
        updateADASInfo();
    }

    /**
     * Updates the ADAS information for the HMI
     */
    private void updateADASInfo() {
        // Update vehicle data in the HMI
        if (adasInterface != null) {
            adasInterface.updateVehicleData(
                    vehicleSpeed,
                    steeringAngle,
                    yawRate,
                    latAccel,
                    longAccel,
                    lastSegmentType,
                    currentSimTime);

            // Update ADAS warnings if we have saved segment data
            if (!isFirstRun && curveWarningAssist != null && currentGPS != null) {
                // Get the ADAS warning message
                String adasMessage = curveWarningAssist.update(currentGPS, currentSimTime);

                // Get the current upcoming segment and distance
                if (curveWarningAssist.hasUpcomingSegment()) {
                    SegmentData upcomingSegment = curveWarningAssist.getUpcomingSegment();
                    double distance = curveWarningAssist.getDistanceToUpcomingSegment();

                    // Update the HMI with this information
                    adasInterface.updateADASWarning(upcomingSegment, distance);
                } else {
                    // No upcoming segment
                    adasInterface.updateADASWarning(null, Double.MAX_VALUE);
                }
            }
        }
    }
}