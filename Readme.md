I'll create a concise README explaining how to run the project.

# SER540 Automotive CAN Trace Analysis Project

## Overview

This project analyzes CAN trace data to detect road segments (straight/curve) and implements an Advanced Driver Assistance System (ADAS) that provides warnings about upcoming road features.

## Prerequisites

- Java 21 or higher
- Gradle 8.x

## Setup and Running the Project

### 1. Clone the repository

```bash
git clone <repository-url>
cd SER540_Team11
```

### 2. Compile the project

```bash
./gradlew build
```

### Running the Project

The project has three modes of operation:

#### A. Simulation Server (First Terminal)

```bash
./gradlew run --args="$(pwd)/app/src/main/resources/18CANmessages.trc $(pwd)/app/src/main/resources/GPStrace.txt"
```

This starts the simulation server that reads CAN and GPS trace files and streams the data via a socket connection.

#### B. Basic Receiver (Second Terminal)

```bash
./gradlew runReceiver
```

This runs the basic receiver that displays sensor data in real-time on the console.

#### C. Enhanced Receiver with Segment Detection (Second Terminal)

```bash
./gradlew runEnhancedReceiver
```

This runs the enhanced receiver that detects road segments and collects segment-specific data. On first run, it collects data; on subsequent runs, it shows ADAS warnings based on previously collected data.

#### D. Receiver with HMI (Second Terminal)

```bash
./gradlew runReceiverHMI
```

This runs the receiver with a graphical HMI interface showing ADAS warnings and vehicle status.

## Project Workflow

1. **First Run**: First, start the simulation server in one terminal, then run either the enhanced receiver or receiver with HMI in another terminal. This first run will collect segment data.

2. **Subsequent Runs**: For subsequent runs, start the simulation again in the first terminal, then run the same receiver in the second terminal. This time, the system will use previously detected segments to provide ADAS warnings.

## Troubleshooting

- If `runReceiver` doesn't work the first time, try running it again (it may need to establish the cache).
- Ensure port 54000 is not already in use or blocked by a firewall.
- If you encounter any issues with file paths, provide absolute paths to the trace files.

## Notes

- For each run, you'll need to press Enter in the receiver terminal to start the simulation.
- After a run completes, you can choose to run another simulation without restarting the application.
- Segment data is stored in memory during the application session and is not persisted between application restarts.
