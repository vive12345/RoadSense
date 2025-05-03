package org.automotive;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.text.DecimalFormat;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private JPanel infoPanel;
    private JPanel steeringPanel;

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

    // Alert icon state
    private boolean showAlert = false;
    private long alertStartTime = 0;
    private static final long ALERT_BLINK_INTERVAL = 500; // milliseconds

    // Alert sound and vibration control
    private boolean isAlertActive = false;
    private long lastAlertTime = 0;
    private static final long ALERT_INTERVAL = 1500; // milliseconds between alerts
    private ScheduledExecutorService executorService;

    // Audio clip for alarm sound
    private Clip alarmClip;

    /**
     * Constructor for the ADAS Interface
     * 
     * @param isFirstRun Whether this is the first run (data collection mode)
     */
    public ADASInterface(boolean isFirstRun) {
        this.isDataCollectionMode = isFirstRun;

        // Initialize thread pool for alerts
        this.executorService = Executors.newScheduledThreadPool(2);

        // Prepare the audio system with loud alert sound
        prepareAudioSystem();

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
                stopAlerts();
                if (executorService != null) {
                    executorService.shutdown();
                }
                if (alarmClip != null) {
                    alarmClip.close();
                }
            }
        });

        // Initialize UI
        initializeUI();

        // Make visible
        setVisible(true);

        // Start the alert blink timer
        startAlertTimer();
    }

    /**
     * Prepare the audio system for VERY LOUD alerts
     */
    private void prepareAudioSystem() {
        try {
            // Create a higher amplitude dual-tone for maximum loudness
            // This combines two frequencies for a more attention-grabbing sound
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);

            // Create an audio stream with a loud dual-tone alarm sound
            // Using 2 seconds of audio for a longer alert
            byte[] buffer = new byte[44100 * 2];

            for (int i = 0; i < buffer.length; i++) {
                // Combine two frequencies: 880Hz and 1760Hz with maximum amplitude
                double angle1 = 2.0 * Math.PI * i / (44100 / 880.0); // 880Hz tone
                double angle2 = 2.0 * Math.PI * i / (44100 / 1760.0); // 1760Hz tone (one octave higher)

                // Use near-maximum amplitude (32767 is max for 16-bit audio)
                // Scale to 90% of maximum to avoid distortion
                short sample = (short) (Math.sin(angle1) * 15000 + Math.sin(angle2) * 15000);

                // Pack the sample into two bytes (little endian)
                buffer[i] = (byte) (sample & 0xFF);
                i++;
                if (i < buffer.length) {
                    buffer[i] = (byte) ((sample >> 8) & 0xFF);
                }
            }

            // Create a clip that can be played repeatedly
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            if (AudioSystem.isLineSupported(info)) {
                alarmClip = (Clip) AudioSystem.getLine(info);
                AudioInputStream ais = new AudioInputStream(
                        new ByteArrayInputStream(buffer),
                        format, buffer.length / format.getFrameSize());
                alarmClip.open(ais);

                // Set volume to maximum
                if (alarmClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) alarmClip.getControl(FloatControl.Type.MASTER_GAIN);
                    // Set gain to maximum value (in decibels)
                    gainControl.setValue(gainControl.getMaximum());
                }
            }
        } catch (Exception e) {
            System.err.println("Error setting up audio system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Starts a timer for blinking alert icons and triggering alerts
     */
    private void startAlertTimer() {
        Timer timer = new Timer(100, e -> {
            // Update alert state every ALERT_BLINK_INTERVAL
            if (System.currentTimeMillis() - alertStartTime > ALERT_BLINK_INTERVAL) {
                showAlert = !showAlert;
                alertStartTime = System.currentTimeMillis();

                // Check if we need to trigger alerts
                if (upcomingSegment != null &&
                        upcomingSegment.getType() == SegmentDetector.SegmentType.CURVE &&
                        distanceToSegment <= IMMEDIATE_WARNING_DISTANCE) {

                    // Only trigger alerts when blinking on and not already alerting
                    if (showAlert && !isAlertActive &&
                            System.currentTimeMillis() - lastAlertTime > ALERT_INTERVAL) {
                        triggerAlerts();
                    }

                    // Always repaint for blinking effect
                    steeringPanel.repaint();
                } else {
                    // Stop any active alerts if we're no longer in warning state
                    stopAlerts();
                }
            }
        });
        timer.start();
    }

    /**
     * Trigger vibration and sound alerts
     */
    private void triggerAlerts() {
        if (isAlertActive)
            return; // Don't stack alerts

        isAlertActive = true;
        lastAlertTime = System.currentTimeMillis();

        // Start both vibration and sound alerts in parallel
        playAlertSound();
        startVibration();

        // Schedule the end of the alert status after 1 second
        executorService.schedule(() -> {
            isAlertActive = false;
        }, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop all active alerts
     */
    private void stopAlerts() {
        // Stop the sound if it's playing
        if (alarmClip != null && alarmClip.isRunning()) {
            alarmClip.stop();
        }
    }

    // Play a VERY LOUD alert sound

    private void playAlertSound() {
        if (alarmClip != null) {
            // Reset to beginning and play
            alarmClip.setFramePosition(0);
            alarmClip.start();

            // Also use the system beep as a backup alert method
            try {
                // Multiple beeps for added attention
                for (int i = 0; i < 3; i++) {
                    final int delay = i * 200;
                    executorService.schedule(() -> {
                        Toolkit.getDefaultToolkit().beep();
                    }, delay, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                // Fallback if beep fails
                System.err.println("Error playing system beep: " + e.getMessage());
            }

            // Announce the alert verbally (in a real system this would use TTS)
            System.out.println("!!! LOUD AUDIO ALERT: CURVE AHEAD! REDUCE SPEED NOW! !!!");
        }
    }

    // Start intense window vibration effect

    private void startVibration() {
        // Get original position
        final Point originalLocation = getLocation();
        final int vibrationIntensity = 8; // Higher pixels to move for stronger effect

        // Create stronger vibration by moving window back and forth rapidly in multiple
        // directions
        for (int i = 0; i < 10; i++) { // More iterations for longer effect
            final int delay = i * 30; // milliseconds between movements (faster)

            // Move in multiple directions for more noticeable effect
            executorService.schedule(() -> {
                setLocation(originalLocation.x + vibrationIntensity, originalLocation.y);
            }, delay, TimeUnit.MILLISECONDS);

            executorService.schedule(() -> {
                setLocation(originalLocation.x - vibrationIntensity, originalLocation.y);
            }, delay + 15, TimeUnit.MILLISECONDS);

            executorService.schedule(() -> {
                setLocation(originalLocation.x, originalLocation.y + vibrationIntensity);
            }, delay + 30, TimeUnit.MILLISECONDS);

            executorService.schedule(() -> {
                setLocation(originalLocation.x, originalLocation.y - vibrationIntensity);
            }, delay + 45, TimeUnit.MILLISECONDS);
        }

        // Reset position after vibration completes
        executorService.schedule(() -> {
            setLocation(originalLocation);
        }, 500, TimeUnit.MILLISECONDS);
    }

    // Initialize the user interface components
    private void initializeUI() {
        // Create the main panel with a BorderLayout
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);

        // Create the warning panel (top)
        createWarningPanel();

        // Create the info panel (left)
        createInfoPanel();

        // Create the steering wheel panel (center)
        createSteeringPanel();

        // Add panels to main panel
        mainPanel.add(warningPanel, BorderLayout.NORTH);
        mainPanel.add(infoPanel, BorderLayout.WEST);
        mainPanel.add(steeringPanel, BorderLayout.CENTER);

        // Set the content pane
        setContentPane(mainPanel);
    }

    // Create the warning panel that shows urgent alerts

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

    // Create the info panel showing current vehicle metrics

    private void createInfoPanel() {
        infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(BACKGROUND_COLOR);
        infoPanel.setPreferredSize(new Dimension(250, WINDOW_HEIGHT - 80));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

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

        // Next segment type
        JLabel nextSegmentTitle = createLabel("Next Segment", 14, Font.BOLD);
        nextSegmentTitle.setForeground(TEXT_COLOR);
        nextSegmentLabel = createLabel("Unknown", 18, Font.PLAIN);
        nextSegmentLabel.setForeground(INFO_COLOR);

        // Direction for curves
        JLabel directionTitle = createLabel("Curve Direction", 14, Font.BOLD);
        directionTitle.setForeground(TEXT_COLOR);
        curveDirectionLabel = createLabel("-", 18, Font.PLAIN);
        curveDirectionLabel.setForeground(INFO_COLOR);

        // Recording status
        recordingLabel = createLabel(isDataCollectionMode ? "COLLECTING DATA" : "USING SAVED DATA", 16, Font.BOLD);
        recordingLabel.setForeground(isDataCollectionMode ? CAUTION_COLOR : INFO_COLOR);

        // Add components to panel
        infoPanel.add(Box.createVerticalStrut(10));
        infoPanel.add(timeTitle);
        infoPanel.add(currentTimeLabel);
        infoPanel.add(Box.createVerticalStrut(30));
        infoPanel.add(speedTitle);
        infoPanel.add(speedLabel);
        infoPanel.add(Box.createVerticalStrut(30));
        infoPanel.add(segmentTitle);
        infoPanel.add(segmentTypeLabel);
        infoPanel.add(Box.createVerticalStrut(30));
        infoPanel.add(nextSegmentTitle);
        infoPanel.add(nextSegmentLabel);
        infoPanel.add(Box.createVerticalStrut(30));
        infoPanel.add(directionTitle);
        infoPanel.add(curveDirectionLabel);
        infoPanel.add(Box.createVerticalStrut(50));
        infoPanel.add(recordingLabel);

        // Add alert information label (visible so users know alerts are enabled)
        JLabel alertInfoLabel = createLabel("AUDIO/HAPTIC ALERTS", 14, Font.BOLD);
        alertInfoLabel.setForeground(WARNING_COLOR);
        infoPanel.add(Box.createVerticalStrut(30));
        infoPanel.add(alertInfoLabel);
    }

    // Create the steering wheel panel for visual representation

    private void createSteeringPanel() {
        steeringPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSteeringWheel(g);
            }
        };
        steeringPanel.setBackground(BACKGROUND_COLOR);
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
     * Draw a simplified steering wheel with direction indicators
     * 
     * @param g Graphics context
     */
    private void drawSteeringWheel(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = steeringPanel.getWidth();
        int height = steeringPanel.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // Size of the steering wheel
        int wheelSize = Math.min(width, height) - 100;
        int wheelX = centerX - wheelSize / 2;
        int wheelY = centerY - wheelSize / 2;

        // Draw background grid
        g2d.setColor(new Color(40, 40, 50));
        for (int i = 0; i < width; i += 50) {
            g2d.drawLine(i, 0, i, height);
        }
        for (int i = 0; i < height; i += 50) {
            g2d.drawLine(0, i, width, i);
        }

        // Draw the steering wheel with rotation based on current steering angle
        // Maximum rotation is 90 degrees in each direction
        double rotationAngle = Math.toRadians(Math.min(90, Math.max(-90, currentSteeringAngle)));

        // Save the current transform
        AffineTransform oldTransform = g2d.getTransform();

        // Translate and rotate around the center of the wheel
        g2d.translate(centerX, centerY);
        g2d.rotate(rotationAngle);
        g2d.translate(-centerX, -centerY);

        // Draw the wheel outline
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillOval(wheelX, wheelY, wheelSize, wheelSize);

        // Draw the wheel rim
        g2d.setColor(Color.GRAY);
        g2d.setStroke(new BasicStroke(15));
        g2d.drawOval(wheelX, wheelY, wheelSize, wheelSize);

        // Draw spokes
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Horizontal spoke
        g2d.drawLine(wheelX, centerY, wheelX + wheelSize, centerY);

        // Vertical spoke
        g2d.drawLine(centerX, wheelY, centerX, wheelY + wheelSize);

        // Draw center hub
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillOval(centerX - 40, centerY - 40, 80, 80);

        // Reset transform
        g2d.setTransform(oldTransform);

        // Draw the distance indicator if we have an upcoming segment
        if (upcomingSegment != null && !isDataCollectionMode) {
            drawDistanceIndicator(g2d, centerX, centerY - wheelSize / 2 - 60, distanceToSegment);

            // Draw warning sign if close to a curve
            if (upcomingSegment.getType() == SegmentDetector.SegmentType.CURVE &&
                    distanceToSegment <= IMMEDIATE_WARNING_DISTANCE && showAlert) {
                drawWarningSign(g2d, centerX, centerY);
            }
        }

        // Draw direction indicator for current segment
        drawDirectionIndicator(g2d, centerX, centerY + wheelSize / 2 + 60);
    }

    /**
     * Draw a distance indicator showing how far to the next segment
     * 
     * @param g2d      Graphics context
     * @param x        Center X position
     * @param y        Top Y position
     * @param distance Distance in meters
     */
    private void drawDistanceIndicator(Graphics2D g2d, int x, int y, double distance) {
        int width = 200;
        int height = 40;

        // Choose color based on distance and segment type
        Color bgColor;
        if (upcomingSegment.getType() == SegmentDetector.SegmentType.CURVE) {
            if (distance <= IMMEDIATE_WARNING_DISTANCE) {
                bgColor = WARNING_COLOR;
            } else if (distance <= APPROACHING_WARNING_DISTANCE) {
                bgColor = CAUTION_COLOR;
            } else {
                bgColor = CURVE_COLOR;
            }
        } else {
            bgColor = STRAIGHT_COLOR;
        }

        // Draw background
        g2d.setColor(bgColor);
        g2d.fillRoundRect(x - width / 2, y, width, height, 10, 10);

        // Draw border
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(x - width / 2, y, width, height, 10, 10);

        // Draw text
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.setColor(Color.WHITE);

        String text = df.format(distance) + " m";
        int textWidth = g2d.getFontMetrics().stringWidth(text);
        g2d.drawString(text, x - textWidth / 2, y + height - 12);
    }

    /**
     * Draw a warning sign for immediate curve warnings
     * 
     * @param g2d Graphics context
     * @param x   Center X position
     * @param y   Center Y position
     */
    private void drawWarningSign(Graphics2D g2d, int x, int y) {
        int size = 100; // Larger warning sign

        // Draw red triangle warning sign
        int[] xPoints = { x, x - size / 2, x + size / 2 };
        int[] yPoints = { y - size / 2, y + size / 2, y + size / 2 };

        g2d.setColor(WARNING_COLOR);
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawPolygon(xPoints, yPoints, 3);

        // Draw exclamation mark
        g2d.setFont(new Font("Arial", Font.BOLD, 50)); // Larger font
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth("!");
        int textHeight = fm.getHeight();
        g2d.drawString("!", x - textWidth / 2, y + textHeight / 4);
    }

    /**
     * Draw a direction indicator showing the type of current segment
     * 
     * @param g2d Graphics context
     * @param x   Center X position
     * @param y   Center Y position
     */
    private void drawDirectionIndicator(Graphics2D g2d, int x, int y) {
        int width = 150;
        int height = 40;

        // Choose color and text based on segment type
        Color bgColor;
        String text;

        if (currentSegmentType == SegmentDetector.SegmentType.CURVE) {
            bgColor = CURVE_COLOR;
            text = "CURVE";
        } else if (currentSegmentType == SegmentDetector.SegmentType.STRAIGHT) {
            bgColor = STRAIGHT_COLOR;
            text = "STRAIGHT";
        } else {
            bgColor = Color.GRAY;
            text = "UNKNOWN";
        }

        // Draw background
        g2d.setColor(bgColor);
        g2d.fillRoundRect(x - width / 2, y, width, height, 10, 10);

        // Draw border
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(x - width / 2, y, width, height, 10, 10);

        // Draw text
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.setColor(Color.WHITE);

        int textWidth = g2d.getFontMetrics().stringWidth(text);
        g2d.drawString(text, x - textWidth / 2, y + height - 12);
    }

    // Update the interface with new vehicle and segment data

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
        steeringPanel.repaint();
    }

    // Update the interface with ADAS curve warning information

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
                String direction = segment.getCurveDirection();
                String arrow = direction.equals("right") ? "→" : "←";
                curveDirectionLabel.setText(arrow + " " + direction.toUpperCase() + " " + arrow);
                curveDirectionLabel.setForeground(isCurve ? CURVE_COLOR : STRAIGHT_COLOR);
            } else {
                curveDirectionLabel.setText("-");
                curveDirectionLabel.setForeground(TEXT_COLOR);
            }

            // Update warning label based on distance and segment type
            if (isCurve) {
                if (distance <= IMMEDIATE_WARNING_DISTANCE) {
                    warningLabel.setText("⚠️ CURVE IMMINENT! ⚠️");
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
        steeringPanel.repaint();
    }
}