package org.automotive;

/**
 * ReceiverWithHMI extends the enhanced receiver to add a graphical HMI
 * for the ADAS Curve Warning System.
 */
public class ReceiverWithHMI extends ReceiverEnhanced {

    private ADASInterface adasInterface;

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

        adasInterface = new ADASInterface(isFirstRun);
    }

    @Override
    protected void processAdditionalData() {

        super.processAdditionalData();

        updateADASInfo();
    }

    /**
     * Updates the ADAS information for the HMI
     */
    private void updateADASInfo() {

        if (adasInterface != null) {
            adasInterface.updateVehicleData(
                    vehicleSpeed,
                    steeringAngle,
                    yawRate,
                    latAccel,
                    longAccel,
                    lastSegmentType,
                    currentSimTime);

            if (!isFirstRun && curveWarningAssist != null && currentGPS != null) {

                if (curveWarningAssist.hasUpcomingSegment()) {
                    SegmentData upcomingSegment = curveWarningAssist.getUpcomingSegment();
                    double distance = curveWarningAssist.getDistanceToUpcomingSegment();

                    adasInterface.updateADASWarning(upcomingSegment, distance);
                } else {

                    adasInterface.updateADASWarning(null, Double.MAX_VALUE);
                }
            }
        }
    }
}