package org.automotive;

import java.io.*;
import java.util.*;
import java.util.regex.*;

// this class reads and parses the can trace file
class CANTraceParser {
    // set of valid can ids to filter messages
    private static final Set<String> VALID_IDS = new HashSet<>(Arrays.asList("0018", "0F7A", "0B41"));

    // method to read the can trace file and create a trace object
    public static CANTrace parseCANTraceFile(String filePath) throws IOException {
        CANTrace trace = new CANTrace();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));

        String line;
        // read each line of the file
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // skip empty lines and comments
            if (line.isEmpty() || line.startsWith(";"))
                continue;

            // pattern to extract id and data from the line
            Pattern pattern = Pattern.compile(".*\\s+(\\w{4})\\s+8\\s+((?:\\w{2}\\s*){8})");
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                String id = matcher.group(1);
                String data = matcher.group(2).replaceAll(" ", "");
                double timestamp = (double) (Double.parseDouble(line.split("\\s+")[1]));

                // skip if the id is not in valid ids
                if (!VALID_IDS.contains(id)) {
                    continue;
                }

                // create frames based on id
                switch (id) {
                    case "0018":
                        trace.addFrame(new SteeringWheelAngleFrame(
                                id,
                                timestamp,
                                extractSteeringAngle(data)));
                        break;
                    case "0F7A":
                        trace.addFrame(new VehicleSpeedFrame(
                                id,
                                timestamp,
                                extractVehicleSpeed(data)));
                        break;
                    case "0B41":
                        extractYawAndAcceleration(trace, id, timestamp, data);
                        break;
                }
            }
        }

        reader.close();
        return trace;
    }

    // method to extract steering angle from data
    private static double extractSteeringAngle(String data) {
        int rawValue = ((Integer.parseInt(data.substring(0, 2), 16) << 8) |
                Integer.parseInt(data.substring(2, 4), 16)) & 0x3FFF;
        return rawValue * 0.5 - 2048;
    }

    // method to extract vehicle speed from data
    private static double extractVehicleSpeed(String data) {
        int rawValue = ((Integer.parseInt(data.substring(0, 2), 16) << 8) |
                Integer.parseInt(data.substring(2, 4), 16)) & 0x0FFF;
        return rawValue * 0.1;
    }

    // method to extract yaw rate and acceleration data
    private static void extractYawAndAcceleration(CANTrace trace, String id, double timestamp, String data) {
        int yawRateRaw = Integer.parseInt(data.substring(0, 4), 16) & 0xFFFF;
        double yawRate = yawRateRaw * 0.01 - 327.68;

        int longAccRaw = Integer.parseInt(data.substring(8, 10), 16) & 0xFF;
        double longAcc = longAccRaw * 0.08 - 10.24;

        int latAccRaw = Integer.parseInt(data.substring(10, 12), 16) & 0xFF;
        double latAcc = latAccRaw * 0.08 - 10.24;

        trace.addFrame(new VehicleDynamicsFrame(id, timestamp, latAcc, longAcc, yawRate));
    }
}
