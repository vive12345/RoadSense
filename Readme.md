# Project README

## Overview

This project is a vehicle simulation system that parses and decodes CAN bus messages from a trace file (`.trc`) using the encoding information provided in the `CAN Frames Info.txt` file.

## Current Implementation

- Parses CAN messages from .trc files
- Decodes sensor values including:
  - Steering wheel angle (in degrees)
  - Vehicle speed (in km/h)
  - Vehicle dynamics (yaw rate, lateral and longitudinal acceleration)
- Preserves message timestamps for accurate replay

## Building the Project

```
./gradlew build
```

## Running the Application

```
./gradlew run --args="src/main/resources/18CANmessages.trc"
```

## Project Structure

```
root/
├── app/
│   ├── build.gradle                  # Gradle build config for the app
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── org/
│       │   │       └── automotive/
│       │   │           ├── App.java                 # Initial app class
│       │   │           ├── CANFrame.java            # Base class for CAN frames
│       │   │           ├── CANSimulation.java       # Main application class
│       │   │           ├── CANTrace.java            # Manages frame collection
│       │   │           ├── CANTraceParser.java      # Parses and decodes CAN data
│       │   │           ├── SteeringWheelAngleFrame.java  # Steering angle data
│       │   │           ├── VehicleDynamicsFrame.java     # Yaw and acceleration data
│       │   │           └── VehicleSpeedFrame.java        # Vehicle speed data
│       │   └── resources/
│       │       ├── CAN Frames Info.txt              # Decoding information
│       │       └── 18CANmessages.trc                # Input CAN trace file
│       └── test/
│           └── java/
│               └── org/
│                   └── automotive/
│                       └── AppTest.java             # Test class
├── gradle/
│   ├── libs.versions.toml            # Library version definitions
│   └── wrapper/                      # Gradle wrapper files
├── .gitattributes                    # Git attributes config
├── .gitignore                        # Git ignore patterns
├── build.gradle                      # Root Gradle build file
├── gradlew                           # Gradle wrapper script (Unix)
├── gradlew.bat                       # Gradle wrapper script (Windows)
├── gradle.properties                 # Gradle properties
└── settings.gradle                   # Gradle settings
```

## Output

![alt text](image.png)

## Future Development

This project will be extended to include GPS data and real-time simulation capabilities.
