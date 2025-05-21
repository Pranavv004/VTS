package com.example.Client.Handling;

import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

import com.example.Client.packets.LocationPacket;

public class PacketHandler {
    private static final Random RANDOM = new Random();
    private static final int MAX_PACKET_SIZE = 236;

    public static void sendLocationPacket(String imei, DataOutputStream out, int port) throws Exception {
        // Options for random selection
        String[] packetStatuses = {"H", "L"};
        String[] latitudeDirs = {"N", "S"};
        String[] longitudeDirs = {"E", "W"};
        String[] ignitionStatuses = {"0", "1"};
        String[] mainPowerStatuses = {"0", "1"};
        String[] emergencyStatuses = {"0", "1"};
        String[] tamperAlerts = {"C", "O"};
        String[] digitalInputStatuses = {"0000", "0100", "1000", "1111"};
        String[] digitalOutputStatuses = {"00", "01", "10", "11"};

        // Constant values from the sample
        String startChar = "$";
        String header = "DP";
        String vendorId = "TRAQINN";
        String firmwareVersion = "6.109";
        String packetType = "NR";
        String packetId = "1";
        String vehicleRegNo = "KL01AJ3360";
        String networkOperator = "CellOne";
        String mcc = "404";
        String mnc = "72";
        String lac = "1C74";
        String cellId = "BE9E";
        String checksum = "fe10c4f2";
        String endChar = "*";

        // Generate random data for other fields
        String packetStatus = packetStatuses[RANDOM.nextInt(packetStatuses.length)];
        String gpsFix = String.valueOf(RANDOM.nextInt(2)); // 0 or 1
        String date = String.format("%02d%02d%04d", RANDOM.nextInt(28) + 1, RANDOM.nextInt(12) + 1, RANDOM.nextInt(5) + 2020); // DDMMYYYY
        String time = String.format("%02d%02d%02d", RANDOM.nextInt(24), RANDOM.nextInt(60), RANDOM.nextInt(60)); // HHMMSS
        double latitude = 9.0 + (RANDOM.nextDouble() * 0.1); // Between 9.0 and 9.1
        String latitudeDir = latitudeDirs[RANDOM.nextInt(latitudeDirs.length)];
        double longitude = 76.4 + (RANDOM.nextDouble() * 0.2); // Between 76.4 and 76.6
        String longitudeDir = longitudeDirs[RANDOM.nextInt(longitudeDirs.length)];
        double speed = RANDOM.nextDouble() * 100; // 0 to 100 km/h
        double heading = RANDOM.nextDouble() * 360; // 0 to 360 degrees
        int noOfSatellites = RANDOM.nextInt(12) + 3; // 3 to 14
        double altitude = RANDOM.nextDouble() * 500; // 0 to 500 meters
        double pdop = RANDOM.nextDouble() * 5; // 0 to 5
        double hdop = RANDOM.nextDouble() * 2; // 0 to 2
        String ignitionStatus = ignitionStatuses[RANDOM.nextInt(ignitionStatuses.length)];
        String mainPowerStatus = mainPowerStatuses[RANDOM.nextInt(mainPowerStatuses.length)];
        double mainInputVoltage = 10 + (RANDOM.nextDouble() * 5); // 10 to 15V
        double internalBatteryVoltage = 3 + (RANDOM.nextDouble() * 2); // 3 to 5V
        String emergencyStatus = emergencyStatuses[RANDOM.nextInt(emergencyStatuses.length)];
        String tamperAlert = tamperAlerts[RANDOM.nextInt(tamperAlerts.length)];
        int gsmSignalStrength = RANDOM.nextInt(32); // 0 to 31
        String[] neighborCellIds = new String[4];
        String[] neighborLacs = new String[4];
        String[] neighborSignalStrengths = new String[4];
        for (int i = 0; i < 4; i++) {
            neighborCellIds[i] = String.format("%04X", RANDOM.nextInt(65536));
            neighborLacs[i] = String.format("%04X", RANDOM.nextInt(65536));
            neighborSignalStrengths[i] = String.valueOf(RANDOM.nextInt(100)); // 0 to 99
        }
        String digitalInputStatus = digitalInputStatuses[RANDOM.nextInt(digitalInputStatuses.length)];
        String digitalOutputStatus = digitalOutputStatuses[RANDOM.nextInt(digitalOutputStatuses.length)];
        String frameNumber = String.format("%06d", RANDOM.nextInt(1000000)); // 000001 to 999999

        
        
        
        // Create the location packet with constant and random data
        LocationPacket packet = new LocationPacket(
            startChar, header, vendorId, firmwareVersion, packetType, packetId, packetStatus, imei, vehicleRegNo, gpsFix,
            date, time, latitude, latitudeDir, longitude, longitudeDir, speed, heading, noOfSatellites,
            altitude, pdop, hdop, networkOperator, ignitionStatus, mainPowerStatus, mainInputVoltage,
            internalBatteryVoltage, emergencyStatus, tamperAlert, gsmSignalStrength, mcc, mnc, lac, cellId,
            neighborCellIds[0], neighborLacs[0], neighborSignalStrengths[0], neighborCellIds[1], neighborLacs[1],
            neighborSignalStrengths[1], neighborCellIds[2], neighborLacs[2], neighborSignalStrengths[2],
            neighborCellIds[3], neighborLacs[3], neighborSignalStrengths[3], digitalInputStatus, digitalOutputStatus,
            frameNumber, checksum, endChar
        );

        String packetString = packet.toString();
        byte[] packetBytes = packetString.getBytes(StandardCharsets.UTF_8);
//        System.out.println("Client " + imei + " - Size of actual location packet data: " + packetBytes.length + " bytes\n");
        if (packetBytes.length <= MAX_PACKET_SIZE) {
            out.writeInt(packetBytes.length);
            out.write(packetBytes);
            out.flush();
            System.out.println("Client " + imei + " sent location packet to port " + port + ": " + packetString + "\n");
        } else {
            String sequenceId = UUID.randomUUID().toString();
            int totalLength = packetBytes.length;
            int totalParts = (int) Math.ceil((double) totalLength / MAX_PACKET_SIZE);
            for (int i = 0; i < totalParts; i++) {
                int start = i * MAX_PACKET_SIZE;
                int length = Math.min(MAX_PACKET_SIZE, totalLength - start);
                byte[] partBytes = new byte[length];
                System.arraycopy(packetBytes, start, partBytes, 0, length);

                String partHeader = String.format("$,PART,%s,%d,%d,%s,", sequenceId, i + 1, totalParts, imei);
                byte[] headerBytes = partHeader.getBytes(StandardCharsets.UTF_8);
//                System.out.println("Client " + imei + " - Size of header for packet part " + (i + 1) + ": " + headerBytes.length + " bytes\n");
//                System.out.println("Client " + imei + " - Size of data for packet part " + (i + 1) + ": " + length + " bytes\n");
                byte[] combinedBytes = new byte[headerBytes.length + length];
                System.arraycopy(headerBytes, 0, combinedBytes, 0, headerBytes.length);
                System.arraycopy(partBytes, 0, combinedBytes, headerBytes.length, length);

//                System.out.println("Client " + imei + " - Total size of packet part " + (i + 1) + ": " + combinedBytes.length + " bytes\n");
                out.writeInt(combinedBytes.length);
                out.write(combinedBytes);
                out.flush();
                System.out.println("Client " + imei + " sent packet part " + (i + 1) + " of " + totalParts + " to port " + port + "\n");
            }
        }
    }
}