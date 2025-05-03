# SER540 Automotive CAN Trace Analysis Project

## Project Overview

This project simulates and analyzes CAN trace data from a vehicle test drive, implementing two phases:

1. **Phase 1**: Simulation and real-time display of sensor values
2. **Phase 2**: Segment detection, data analysis, and ADAS (Advanced Driver Assistance System) implementation

The system detects straight and curve segments from sensor data, extracts valuable metrics for each segment, and implements a Curve Warning Assistant that alerts drivers about upcoming road features.

## Prerequisites

- Java 21 or higher
- Gradle 8.x

## Project Structure

### Core Components

### Key Classes

| Class                     | Description                                                |
| ------------------------- | ---------------------------------------------------------- |
| `CANTraceSimulation`      | Main simulation server that sends data with correct timing |
| `Receiver`                | Basic client that displays sensor data (Phase 1)           |
| `ReceiverBase`            | Abstract base class with common receiver functionality     |
| `ReceiverEnhanced`        | Client with segment detection and ADAS (Phase 2)           |
| `ReceiverWithHMI`         | Client with graphical HMI interface                        |
| `SegmentDetector`         | Detects road segments from sensor data                     |
| `SegmentData`             | Stores segment-specific data and metrics                   |
| `SegmentCollection`       | Manages all detected segments                              |
| `CurveWarningAssist`      | ADAS system that provides curve warnings                   |
| `ADASInterface`           | GUI implementation for the ADAS HMI                        |
| `CANFrame`                | Base class for all CAN frame types                         |
| `SteeringWheelAngleFrame` | Frame containing steering wheel angle data                 |
| `VehicleSpeedFrame`       | Frame containing vehicle speed data                        |
| `VehicleDynamicsFrame`    | Frame containing vehicle dynamics data                     |
| `CANTrace`                | Data structure for CAN frames                              |
| `CANTraceParser`          | Parser to read and decode CAN trace files                  |
| `GPScoordinates`          | Class for GPS coordinate data                              |
| `GPSTrace`                | Data structure for GPS coordinates                         |
| `GPSParser`               | Parser to read GPS coordinate files                        |
| `MathUtils`               | Utility class for mathematical operations                  |
| `GPSUtils`                | Utility class for GPS-related calculations                 |

## Setup and Running the Project

### 1. Compile the project

```bash
./gradlew build
```

### 2. Running the Simulation

You need to run both the simulator (server) and a receiver (client).

#### Simulator (First Terminal)

```bash
./gradlew run --args="$(pwd)/app/src/main/resources/18CANmessages.trc $(pwd)/app/src/main/resources/GPStrace.txt"
```

This starts the simulation server that reads CAN and GPS trace files and streams the data.

#### Running the Receiver with HMI (Second Terminal)

To run the full experience with the graphical HMI interface:

```bash
./gradlew runReceiverHMI
```

When you run this command:

1. The HMI UI window will open automatically
2. **Do not close the UI window**
3. Go to the console and press Enter to start the simulation
4. The first phase will start collecting segment data in real-time
5. At the end of the simulation, it will print detailed segment data with all sensor metrics
6. When prompted with "Do you want to run another simulation? (y/n)", type `y` and press Enter
7. This will start the second phase (ADAS mode) using the collected data
8. A new UI will show warnings about upcoming segments based on previously collected data

## Project Phase 2 Features

### 1. Segment Detection

The system identifies two types of road segments based on sensor data:

- **Straight**: Vehicle drives along a straight line
- **Curve**: Vehicle drives through a curve

Detection uses a sliding window approach to analyze:

- Steering wheel angle
- Vehicle yaw rate
- Lateral acceleration

The `SegmentDetector` class tracks consecutive measurements that exceed threshold values to determine the current segment type, ensuring stable detection even with noisy sensor data.

### 2. Data Extraction

For each segment, the system collects and calculates:

#### Straight Segments

- GPS coordinates (start/end)
- Average/max/min vehicle speed
- Max/min longitudinal acceleration
- Segment length

#### Curve Segments

- GPS coordinates (start/end)
- Average/max/min vehicle speed
- Curve direction (left/right)
- Max/min lateral acceleration
- Degrees of curve
- Average/max yaw rate

### 3. ADAS: Curve Warning Assistant

The ADAS system provides real-time information about upcoming segments:

- Distance to upcoming segments calculated using GPS coordinates
- Curve direction warnings (left/right)
- Warning levels based on distance thresholds:
  - **Immediate warning** (< 50m): Critical alerts for imminent curves
  - **Approaching warning** (< 100m): Prepare to slow down
  - **Advance warning** (< 200m): Early notification of upcoming curves

The `CurveWarningAssist` class continuously monitors the vehicle's position relative to known segments and updates warnings in real-time.

### 4. HMI (Extra Credit)

A graphical user interface implemented in Swing that provides:

- Real-time vehicle status display (speed, steering angle, etc.)
- Visual representation of steering wheel with accurate rotation
- Color-coded warning indicators for approaching curves
- Distance countdown to upcoming segments
- Visual alerts with animated warning icons for imminent curves
- Audible alerts with increasing intensity based on proximity
- Haptic feedback (window vibration) for critical warnings
- Segment type indication with color coding (green for straight, red for curves)

## Troubleshooting

- Make sure to run the simulator (server) first before starting the receiver
- Do not close the HMI UI window during the simulation
- Ensure port 54000 is not already in use or blocked by a firewall
- If you encounter issues with file paths, provide absolute paths to the trace files
- For clean builds, run: `./gradlew clean build`
- If the simulation doesn't respond after pressing Enter, restart both the simulator and receiver

### Important Note About HMI Mode

When running in HMI mode:

- The first simulation run collects data (HMI will indicate "COLLECTING DATA")
- The second simulation run uses the collected data for ADAS warnings
- Audio and visual alerts will activate when approaching curves
- The steering wheel visualization shows the current steering angle

## Console Output

When running the Enhanced Receiver:

```
Time | Speed | SteerAngle | YawRate | LatAccel | LongAccel | GPS | Segment | ADAS Info
```

When data collection is complete, all segment data will be printed with detailed metrics.

## Project Video

[Project Demonstration Video](https://youtu.be/eILOWdh2HVo)
