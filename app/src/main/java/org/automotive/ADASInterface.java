package org.automotive;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;

/**
 * This class implements a graphical HMI (Human-Machine Interface) for the ADAS
 * Curve Warning system.
 * It provides a visual display of upcoming road segments, warnings, and current
 * vehicle status.
 */
public class ADASInterface extends JFrame {
    // Constants for display
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final Color BACKGROUND_COLOR = new Color(20, 20, 30);
    private static final Color TEXT_COLOR = new Color(230, 230, 230);
    private static final Color WARNING_COLOR = new Color(255, 60, 60);
    private static final Color CAUTION_COLOR = new Color(255, 200, 0);
    private static final Color INFO_COLOR = new Color(100, 200, 255);
    private static final Color STRAIGHT_COLOR = new Color(120, 200, 120);
    private static final Color CURVE_COLOR = new Color(200, 120, 120);

    // Distance thresholds for warnings (in meters)
    private static final double IMMEDIATE_WARNING_DISTANCE = 50.0;
    private static final double APPROACHING_WARNING_DISTANCE = 100.0;
    private static final double ADVANCE_WARNING_DISTANCE = 200.0;

    // Format for numerical display
    private final DecimalFormat df = new DecimalFormat("0.0");

    // UI Components
    private JPanel mainPanel;
    private JPanel warningPanel;
    private JPanel speedometerPanel;
    private JPanel segmentInfoPanel;
    private JPanel mapPanel;

    // Labels for displaying information
    private JLabel speedLabel;
    private JLabel warningLabel;
    private JLabel distanceLabel;
    private JLabel segmentTypeLabel;
    private JLabel curveDirectionLabel;
    private JLabel nextSegmentLabel;
    private JLabel recordingLabel;
    private JLabel currentTimeLabel;

    // Current data values
    private double currentSpeed = 0;
    private double currentYawRate = 0;
    private double currentSteeringAngle = 0;
    private double currentLateralAccel = 0;
    private double currentLongAccel = 0;
    private SegmentDetector.SegmentType currentSegmentType = null;
    private SegmentData upcomingSegment = null;
    private double distanceToSegment = Double.MAX_VALUE;
    private boolean isDataCollectionMode = true;
    private double simulationTime = 0;

    /**
     * Constructor for the ADAS Interface
     * 
     * @param isFirstRun Whether this is the first run (data collection mode)
     */
    public ADASInterface(boolean isFirstRun) {
        this.isDataCollectionMode = isFirstRun;

        // Set up the window
        setTitle("ADAS Curve Warning System");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen

        // Handle window closing event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("ADAS HMI closed. Continuing in console mode.");
            }
        });

        // Initialize UI
        initializeUI();

        // Make visible
        setVisible(true);
    }

    /**
     * Initialize the user interface components
     */
    private void initializeUI() {
        // Create the main panel with a BorderLayout
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);

        // Create the warning panel (top)
        createWarningPanel();

        // Create the speedometer panel (left)
        createSpeedometerPanel();

        // Create segment info panel (right)
        createSegmentInfoPanel();

        // Create map panel (center)
        createMapPanel();

        // Add panels to main panel
        mainPanel.add(warningPanel, BorderLayout.NORTH);
        mainPanel.add(speedometerPanel, BorderLayout.WEST);
        mainPanel.add(segmentInfoPanel, BorderLayout.EAST);
        mainPanel.add(mapPanel, BorderLayout.CENTER);

        // Set the content pane
        setContentPane(mainPanel);
    }

    /**
     * Create the warning panel that shows urgent alerts
     */
    private void createWarningPanel() {
        warningPanel = new JPanel();
        warningPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        warningPanel.setBackground(BACKGROUND_COLOR);
        warningPanel.setPreferredSize(new Dimension(WINDOW_WIDTH, 80));

        warningLabel = createLabel("", 28, Font.BOLD);
        warningLabel.setForeground(TEXT_COLOR);

        distanceLabel = createLabel("", 24, Font.PLAIN);
        distanceLabel.setForeground(TEXT_COLOR);

        warningPanel.add(warningLabel);
        warningPanel.add(distanceLabel);
    }

    /**
     * Create the speedometer panel showing current vehicle metrics
     */
    private void createSpeedometerPanel() {
        speedometerPanel = new JPanel();
        speedometerPanel.setLayout(new BoxLayout(speedometerPanel, BoxLayout.Y_AXIS));
        speedometerPanel.setBackground(BACKGROUND_COLOR);
        speedometerPanel.setPreferredSize(new Dimension(200, WINDOW_HEIGHT - 80));
        speedometerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Simulation time
        JLabel timeTitle = createLabel("Simulation Time", 14, Font.BOLD);
        timeTitle.setForeground(TEXT_COLOR);
        currentTimeLabel = createLabel("0.0 ms", 18, Font.PLAIN);
        currentTimeLabel.setForeground(INFO_COLOR);

        // Speed display
        JLabel speedTitle = createLabel("Current Speed", 14, Font.BOLD);
        speedTitle.setForeground(TEXT_COLOR);
        speedLabel = createLabel("0.0 km/h", 24, Font.BOLD);
        speedLabel.setForeground(INFO_COLOR);

        // Current segment type
        JLabel segmentTitle = createLabel("Current Segment", 14, Font.BOLD);
        segmentTitle.setForeground(TEXT_COLOR);
        segmentTypeLabel = createLabel("Unknown", 18, Font.PLAIN);
        segmentTypeLabel.setForeground(STRAIGHT_COLOR);

        // Recording status
        recordingLabel = createLabel(isDataCollectionMode ? "COLLECTING DATA" : "USING SAVED DATA", 16, Font.BOLD);
        recordingLabel.setForeground(isDataCollectionMode ? CAUTION_COLOR : INFO_COLOR);

        // Add components to panel
        speedometerPanel.add(Box.createVerticalStrut(10));
        speedometerPanel.add(timeTitle);
        speedometerPanel.add(currentTimeLabel);
        speedometerPanel.add(Box.createVerticalStrut(30));
        speedometerPanel.add(speedTitle);
        speedometerPanel.add(speedLabel);
        speedometerPanel.add(Box.createVerticalStrut(30));
        speedometerPanel.add(segmentTitle);
        speedometerPanel.add(segmentTypeLabel);
        speedometerPanel.add(Box.createVerticalStrut(50));
        speedometerPanel.add(recordingLabel);
    }

    /**
     * Create the segment info panel showing upcoming segment details
     */
    private void createSegmentInfoPanel() {
        segmentInfoPanel = new JPanel();
        segmentInfoPanel.setLayout(new BoxLayout(segmentInfoPanel, BoxLayout.Y_AXIS));
        segmentInfoPanel.setBackground(BACKGROUND_COLOR);
        segmentInfoPanel.setPreferredSize(new Dimension(200, WINDOW_HEIGHT - 80));
        segmentInfoPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Next segment label
        JLabel nextSegmentTitle = createLabel("Next Segment", 14, Font.BOLD);
        nextSegmentTitle.setForeground(TEXT_COLOR);
        nextSegmentLabel = createLabel("Unknown", 18, Font.PLAIN);
        nextSegmentLabel.setForeground(INFO_COLOR);

        // Direction for curves
        JLabel directionTitle = createLabel("Curve Direction", 14, Font.BOLD);
        directionTitle.setForeground(TEXT_COLOR);
        curveDirectionLabel = createLabel("-", 18, Font.PLAIN);
        curveDirectionLabel.setForeground(INFO_COLOR);

        // Add components to panel
        segmentInfoPanel.add(Box.createVerticalStrut(10));
        segmentInfoPanel.add(nextSegmentTitle);
        segmentInfoPanel.add(nextSegmentLabel);
        segmentInfoPanel.add(Box.createVerticalStrut(30));
        segmentInfoPanel.add(directionTitle);
        segmentInfoPanel.add(curveDirectionLabel);
    }

    /**
     * Create the map panel showing a visual representation of the road
     */
    private void createMapPanel() {
        mapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawMap(g);
            }
        };
        mapPanel.setBackground(BACKGROUND_COLOR);
    }

    /**
     * Create a formatted JLabel with specified text, size, and style
     * 
     * @param text  The label text
     * @param size  Font size
     * @param style Font style (e.g. Font.BOLD)
     * @return Configured JLabel
     */
    private JLabel createLabel(String text, int size, int style) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Arial", style, size));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Draw the map visualization
     * 
     * @param g Graphics context
     */
    private void drawMap(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = mapPanel.getWidth();
        int height = mapPanel.getHeight();

        // Draw grid lines
        g2d.setColor(new Color(50, 50, 60));
        for (int i = 0; i < width; i += 50) {
            g2d.drawLine(i, 0, i, height);
        }
        for (int i = 0; i < height; i += 50) {
            g2d.drawLine(0, i, width, i);
        }

        // Draw current road
        int roadWidth = 60;
        int centerX = width / 2;
        int centerY = height / 2;

        // Draw the road based on current segment type
        if (currentSegmentType == SegmentDetector.SegmentType.CURVE) {
            drawCurvedRoad(g2d, centerX, centerY, roadWidth);
        } else {
            drawStraightRoad(g2d, centerX, centerY, roadWidth);
        }

        // Draw vehicle position
        drawVehicle(g2d, centerX, centerY + 100);

        // Draw upcoming segment if available
        if (upcomingSegment != null && !isDataCollectionMode) {
            drawUpcomingSegment(g2d, width, height, upcomingSegment);
        }
    }

    /**
     * Draw a straight road section
     * 
     * @param g2d       Graphics context
     * @param centerX   Center X position
     * @param centerY   Center Y position
     * @param roadWidth Width of the road
     */
    private void drawStraightRoad(Graphics2D g2d, int centerX, int centerY, int roadWidth) {
        // Draw road outline
        g2d.setColor(new Color(80, 80, 90));
        g2d.fillRect(centerX - roadWidth / 2, 0, roadWidth, mapPanel.getHeight());

        // Draw centerline
        g2d.setColor(Color.YELLOW);
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[] { 15, 15 }, 0));
        g2d.drawLine(centerX, 0, centerX, mapPanel.getHeight());

        // Draw edge lines
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(centerX - roadWidth / 2, 0, centerX - roadWidth / 2, mapPanel.getHeight());
        g2d.drawLine(centerX + roadWidth / 2, 0, centerX + roadWidth / 2, mapPanel.getHeight());
    }

    /**
     * Draw a curved road section
     * 
     * @param g2d       Graphics context
     * @param centerX   Center X position
     * @param centerY   Center Y position
     * @param roadWidth Width of the road
     */
    private void drawCurvedRoad(Graphics2D g2d, int centerX, int centerY, int roadWidth) {
        // Determine curve direction
        boolean curveRight = upcomingSegment != null &&
                upcomingSegment.getCurveDirection() != null &&
                upcomingSegment.getCurveDirection().equals("right");

        // Draw curve
        int curveRadius = 200;
        int startAngle = 180;
        int arcAngle = 90;

        // Adjust position based on direction
        int curveX = curveRight ? centerX - curveRadius : centerX - curveRadius + roadWidth;

        // Draw road outline
        g2d.setColor(new Color(80, 80, 90));
        g2d.fillArc(curveX, centerY - curveRadius,
                2 * curveRadius, 2 * curveRadius,
                startAngle, arcAngle);

        g2d.fillArc(curveX - roadWidth, centerY - curveRadius,
                2 * curveRadius, 2 * curveRadius,
                startAngle, arcAngle);

        // Draw straight section before curve
        drawStraightRoad(g2d, centerX, centerY + 200, roadWidth);
    }

    /**
     * Draw the vehicle on the map
     * 
     * @param g2d Graphics context
     * @param x   X position
     * @param y   Y position
     */
    private void drawVehicle(Graphics2D g2d, int x, int y) {
        // Draw vehicle as a triangle pointing up
        int vehicleSize = 20;
        int[] xPoints = { x, x - vehicleSize, x + vehicleSize };
        int[] yPoints = { y - vehicleSize, y + vehicleSize, y + vehicleSize };

        g2d.setColor(Color.WHITE);
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawPolygon(xPoints, yPoints, 3);
    }

    /**
     * Draw the upcoming segment on the map
     * 
     * @param g2d     Graphics context
     * @param width   Panel width
     * @param height  Panel height
     * @param segment Upcoming segment
     */
    private void drawUpcomingSegment(Graphics2D g2d, int width, int height, SegmentData segment) {
        int y = 100; // Distance from top

        // Set color based on segment type
        g2d.setColor(segment.getType() == SegmentDetector.SegmentType.CURVE ? CURVE_COLOR : STRAIGHT_COLOR);

        // Draw segment representation
        if (segment.getType() == SegmentDetector.SegmentType.CURVE) {
            // Draw curve icon
            int iconSize = 80;
            boolean curveRight = segment.getCurveDirection() != null &&
                    segment.getCurveDirection().equals("right");

            int x = width / 2 - iconSize / 2;

            g2d.setStroke(new BasicStroke(5));
            if (curveRight) {
                g2d.drawArc(x, y, iconSize, iconSize, 180, 90);
                // Draw arrow
                int[] xPoints = { x + iconSize, x + iconSize - 15, x + iconSize - 5 };
                int[] yPoints = { y + iconSize / 2, y + iconSize / 2 - 10, y + iconSize / 2 + 10 };
                g2d.fillPolygon(xPoints, yPoints, 3);
            } else {
                g2d.drawArc(x, y, iconSize, iconSize, 270, 90);
                // Draw arrow
                int[] xPoints = { x, x + 15, x + 5 };
                int[] yPoints = { y + iconSize / 2, y + iconSize / 2 - 10, y + iconSize / 2 + 10 };
                g2d.fillPolygon(xPoints, yPoints, 3);
            }

            // Display distance
            String distanceText = df.format(distanceToSegment) + " m";
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.drawString(distanceText, width / 2 - 40, y + iconSize + 30);

            // Display curve degree
            String degreeText = df.format(segment.getCurveDegrees()) + "°";
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            g2d.drawString(degreeText, width / 2 - 20, y + iconSize + 60);
        } else {
            // Draw straight segment icon
            int iconWidth = 60;
            int iconHeight = 120;
            int x = width / 2 - iconWidth / 2;

            g2d.fillRect(x, y, iconWidth, iconHeight);

            // Draw lane markings
            g2d.setColor(Color.WHITE);
            g2d.setStroke(
                    new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[] { 10, 10 }, 0));
            g2d.drawLine(width / 2, y, width / 2, y + iconHeight);

            // Display distance
            g2d.setColor(STRAIGHT_COLOR);
            String distanceText = df.format(distanceToSegment) + " m";
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.drawString(distanceText, width / 2 - 40, y + iconHeight + 30);
        }
    }

    /**
     * Update the interface with new vehicle and segment data
     * 
     * @param speed         Current vehicle speed (km/h)
     * @param steeringAngle Current steering wheel angle (degrees)
     * @param yawRate       Current yaw rate (deg/s)
     * @param latAccel      Current lateral acceleration (m/s²)
     * @param longAccel     Current longitudinal acceleration (m/s²)
     * @param segmentType   Current segment type
     * @param time          Current simulation time (ms)
     */
    public void updateVehicleData(double speed, double steeringAngle, double yawRate,
            double latAccel, double longAccel, SegmentDetector.SegmentType segmentType,
            double time) {

        this.currentSpeed = speed;
        this.currentSteeringAngle = steeringAngle;
        this.currentYawRate = yawRate;
        this.currentLateralAccel = latAccel;
        this.currentLongAccel = longAccel;
        this.currentSegmentType = segmentType;
        this.simulationTime = time;

        // Update the UI components
        speedLabel.setText(df.format(speed) + " km/h");
        currentTimeLabel.setText(df.format(time) + " ms");

        if (segmentType != null) {
            segmentTypeLabel.setText(segmentType.toString());
            segmentTypeLabel
                    .setForeground(segmentType == SegmentDetector.SegmentType.CURVE ? CURVE_COLOR : STRAIGHT_COLOR);
        } else {
            segmentTypeLabel.setText("Unknown");
            segmentTypeLabel.setForeground(TEXT_COLOR);
        }

        // Repaint for updated vehicle data
        repaint();
    }

    /**
     * Update the interface with ADAS curve warning information
     * 
     * @param segment  The upcoming segment
     * @param distance Distance to the upcoming segment (meters)
     */
    public void updateADASWarning(SegmentData segment, double distance) {
        this.upcomingSegment = segment;
        this.distanceToSegment = distance;

        if (segment != null) {
            boolean isCurve = segment.getType() == SegmentDetector.SegmentType.CURVE;

            // Update next segment label
            nextSegmentLabel.setText(segment.getType().toString());
            nextSegmentLabel.setForeground(isCurve ? CURVE_COLOR : STRAIGHT_COLOR);

            // Update distance label
            distanceLabel.setText(df.format(distance) + " meters ahead");

            // Update direction label for curves
            if (isCurve && segment.getCurveDirection() != null) {
                curveDirectionLabel.setText(segment.getCurveDirection().toUpperCase());

                // Determine arrow direction
                String arrow = segment.getCurveDirection().equals("right") ? "→" : "←";
                curveDirectionLabel.setText(arrow + " " + segment.getCurveDirection().toUpperCase() + " " + arrow);
            } else {
                curveDirectionLabel.setText("-");
            }

            // Update warning label based on distance and segment type
            if (isCurve) {
                if (distance <= IMMEDIATE_WARNING_DISTANCE) {
                    warningLabel.setText("CURVE IMMINENT!");
                    warningLabel.setForeground(WARNING_COLOR);
                } else if (distance <= APPROACHING_WARNING_DISTANCE) {
                    warningLabel.setText("APPROACHING CURVE");
                    warningLabel.setForeground(CAUTION_COLOR);
                } else if (distance <= ADVANCE_WARNING_DISTANCE) {
                    warningLabel.setText("Curve Ahead");
                    warningLabel.setForeground(INFO_COLOR);
                } else {
                    warningLabel.setText("");
                }
            } else {
                if (distance <= IMMEDIATE_WARNING_DISTANCE) {
                    warningLabel.setText("Straight Ahead");
                    warningLabel.setForeground(STRAIGHT_COLOR);
                } else {
                    warningLabel.setText("");
                }
            }
        } else {
            // No upcoming segment
            nextSegmentLabel.setText("None");
            curveDirectionLabel.setText("-");
            distanceLabel.setText("");
            warningLabel.setText("");
        }

        // Repaint for updated ADAS data
        repaint();
    }
}