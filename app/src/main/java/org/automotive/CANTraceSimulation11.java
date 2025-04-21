// package org.automotive;

// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.concurrent.*;

// /**
// * This class simulates the CAN trace and GPS data with correct timing.
// * It acts as a socket server that sends sensor values to a connected client.
// */
// public class CANTraceSimulation11 {
// // Port number for the socket server
// private static final int PORT = 54000;

// // Socket related variables
// private ServerSocket serverSocket;
// private Socket clientSocket;
// private PrintWriter out;
// private BufferedReader in;

// // Data storage
// private CANTrace canTrace;
// private GPSTrace gpsTrace;

// // Flag to track if simulation is running
// private boolean isSimulationRunning = false;

// // Final message to indicate simulation is complete
// private static final String SIMULATION_COMPLETE_MESSAGE =
// "SIMULATION_COMPLETE";

// // Ready message from client
// private static final String CLIENT_READY_MESSAGE = "READY";

// // Thread for simulation
// private ExecutorService executor;

// /**
// * Constructor that initializes the simulation with CAN and GPS traces
// */
// public CANTraceSimulation11(CANTrace canTrace, GPSTrace gpsTrace) {
// this.canTrace = canTrace;
// this.gpsTrace = gpsTrace;
// this.executor = Executors.newSingleThreadExecutor();
// }

// /**
// * Starts the socket server and waits for client connections
// */
// public void startServer() {
// if (canTrace == null || gpsTrace == null) {
// System.err.println("Error: CAN Trace or GPS Trace is missing");
// return;
// }

// try {
// serverSocket = new ServerSocket(PORT);
// System.out.println("Server started on port " + PORT);
// System.out.println("Waiting for client connection...");

// // Loop to continuously accept new connections
// while (true) {
// try {
// // Wait for a client to connect
// clientSocket = serverSocket.accept();
// System.out.println("Client connected: " + clientSocket.getInetAddress());

// // Initialize input/output streams
// out = new PrintWriter(clientSocket.getOutputStream(), true);
// in = new BufferedReader(new
// InputStreamReader(clientSocket.getInputStream()));

// // Wait for client ready message and start simulation
// String inputLine;
// while ((inputLine = in.readLine()) != null) {
// if (inputLine.equals(CLIENT_READY_MESSAGE)) {
// System.out.println("Received ready message from client");
// startSimulation();
// break;
// }
// }

// // Clean up resources for this client connection
// cleanUp();
// } catch (IOException e) {
// System.err.println("Connection error: " + e.getMessage());
// cleanUp();
// }
// }
// } catch (IOException e) {
// System.err.println("Server error: " + e.getMessage());
// } finally {
// shutdownServer();
// }
// }

// /**
// * Starts the simulation process by sending sensor values with correct timing
// */
// public void startSimulation() {
// if (isSimulationRunning) {
// System.out.println("Simulation is already running");
// return;
// }

// isSimulationRunning = true;

// // Reset traces to start from beginning
// canTrace.resetNextMessage();
// gpsTrace.resetNextCoordinate();

// // Start the simulation in a separate thread
// executor.submit(() -> {
// try {
// System.out.println("Starting simulation...");
// long startTime = System.currentTimeMillis();

// // Create a priority queue to order all events by time offset
// PriorityQueue<SimulationEvent> eventQueue = createEventQueue();

// // Process events in order of time offset
// while (!eventQueue.isEmpty() && isSimulationRunning) {
// SimulationEvent event = eventQueue.poll();

// // Calculate delay time
// long currentTime = System.currentTimeMillis();
// long elapsedTime = currentTime - startTime;
// long delayTime = Math.max(0, event.timeOffset - elapsedTime);

// // Wait until it's time to send this event
// if (delayTime > 0) {
// Thread.sleep(delayTime);
// }

// // Send the event data to client
// sendEventToClient(event);
// }

// // Send completion message if simulation finished normally
// if (isSimulationRunning) {
// out.println(SIMULATION_COMPLETE_MESSAGE);
// System.out.println("Simulation completed");
// }

// } catch (Exception e) {
// System.err.println("Simulation error: " + e.getMessage());
// } finally {
// isSimulationRunning = false;
// }
// });
// }

// /**
// * Creates a priority queue of all simulation events sorted by time offset
// */
// private PriorityQueue<SimulationEvent> createEventQueue() {
// PriorityQueue<SimulationEvent> eventQueue = new PriorityQueue<>(
// Comparator.comparingDouble(SimulationEvent::getTimeOffset));

// // Add CAN frames to the queue
// CANFrame canFrame;
// while ((canFrame = canTrace.getNextMessage()) != null) {
// eventQueue.add(new SimulationEvent(canFrame));
// }

// // Add GPS coordinates to the queue
// GPScoordinates gpsCoord;
// while ((gpsCoord = gpsTrace.getNextCoordinate()) != null) {
// eventQueue.add(new SimulationEvent(gpsCoord));
// }

// // Reset traces after building queue
// canTrace.resetNextMessage();
// gpsTrace.resetNextCoordinate();

// return eventQueue;
// }

// /**
// * Sends an event's data to the connected client
// */
// private void sendEventToClient(SimulationEvent event) {
// if (!isSimulationRunning || out == null) {
// return;
// }

// String message = event.toString();
// out.println(message);
// }

// /**
// * Cleans up resources for current client
// */
// private void cleanUp() {
// isSimulationRunning = false;

// try {
// if (out != null)
// out.close();
// if (in != null)
// in.close();
// if (clientSocket != null)
// clientSocket.close();
// } catch (IOException e) {
// System.err.println("Error during cleanup: " + e.getMessage());
// }

// // Set to null to indicate they're closed
// out = null;
// in = null;
// clientSocket = null;

// System.out.println("Client connection closed");
// }

// /**
// * Shuts down the server
// */
// public void shutdownServer() {
// isSimulationRunning = false;

// try {
// cleanUp();

// if (serverSocket != null && !serverSocket.isClosed()) {
// serverSocket.close();
// }

// executor.shutdown();
// try {
// if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
// executor.shutdownNow();
// }
// } catch (InterruptedException e) {
// executor.shutdownNow();
// }
// } catch (IOException e) {
// System.err.println("Error shutting down server: " + e.getMessage());
// }

// System.out.println("Server shut down");
// }

// /**
// * Inner class to represent a simulation event (CAN frame or GPS coordinate)
// */
// private class SimulationEvent {
// private String type;
// private String id;
// private long timeOffset;
// private Map<String, Double> values;

// // Constructor for CAN frames
// public SimulationEvent(CANFrame frame) {
// this.id = frame.getId();
// this.timeOffset = (long) frame.getTimestamp();
// this.values = new HashMap<>();

// if (frame instanceof SteeringWheelAngleFrame) {
// this.type = "STEERING";
// SteeringWheelAngleFrame steeringFrame = (SteeringWheelAngleFrame) frame;
// this.values.put("angle",
// Double.valueOf(steeringFrame.toString().split("Angle=")[1].split("°")[0]));
// } else if (frame instanceof VehicleSpeedFrame) {
// this.type = "SPEED";
// VehicleSpeedFrame speedFrame = (VehicleSpeedFrame) frame;
// this.values.put("speed",
// Double.valueOf(speedFrame.toString().split("Speed=")[1].split(" km/h")[0]));
// } else if (frame instanceof VehicleDynamicsFrame) {
// this.type = "DYNAMICS";
// VehicleDynamicsFrame dynamicsFrame = (VehicleDynamicsFrame) frame;
// this.values.put("latAccel", dynamicsFrame.getLatAccel());
// this.values.put("longAccel", dynamicsFrame.getLongAccel());
// this.values.put("yawRate", dynamicsFrame.getYawRate());
// }
// }

// // Constructor for GPS coordinates
// public SimulationEvent(GPScoordinates coords) {
// this.type = "GPS";
// this.id = "GPS";
// this.timeOffset = (long) coords.getTimeOffset();
// this.values = new HashMap<>();
// this.values.put("latitude", coords.getLatitude());
// this.values.put("longitude", coords.getLongitude());
// }

// public long getTimeOffset() {
// return timeOffset;
// }

// @Override
// public String toString() {
// StringBuilder sb = new StringBuilder();
// sb.append(type).append(";");
// sb.append(id).append(";");
// sb.append(timeOffset).append(";");

// if (type.equals("GPS")) {
// sb.append(values.get("latitude")).append(";");
// sb.append(values.get("longitude"));
// } else if (type.equals("STEERING")) {
// sb.append(values.get("angle"));
// } else if (type.equals("SPEED")) {
// sb.append(values.get("speed"));
// } else if (type.equals("DYNAMICS")) {
// sb.append(values.get("latAccel")).append(";");
// sb.append(values.get("longAccel")).append(";");
// sb.append(values.get("yawRate"));
// }

// return sb.toString();
// }
// }

// /**
// * Main method for standalone testing
// */
// public static void main(String[] args) {
// if (args.length < 2) {
// System.out.println("Usage: java CANTraceSimulation <can_file_path>
// <gps_file_path>");
// return;
// }

// String canFilePath = args[0];
// String gpsFilePath = args[1];

// try {
// // Parse CAN trace
// CANTrace canTrace = CANTraceParser.parseCANTraceFile(canFilePath);
// System.out.println("CAN trace loaded successfully");

// // Parse GPS trace
// GPSTrace gpsTrace = GPSParser.parseGPSTraceFile(gpsFilePath);
// System.out.println("GPS trace loaded successfully");

// // Create and start simulation
// CANTraceSimulation simulation = new CANTraceSimulation(canTrace, gpsTrace);
// simulation.startServer();

// } catch (IOException e) {
// System.out.println("Error reading files: " + e.getMessage());
// }
// }
// }