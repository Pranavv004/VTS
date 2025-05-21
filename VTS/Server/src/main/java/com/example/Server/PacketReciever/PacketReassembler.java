package com.example.Server.PacketReciever;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketReassembler {
    private final int port;
    private final Map<String, Map<Integer, String>> packetParts = new ConcurrentHashMap<>();

    public PacketReassembler(int port) {
        this.port = port;
    }

    public void handlePacketPart(String packet) {
        String[] parts = packet.split(",", 6);
        if (parts.length < 6) {
            System.err.println("Invalid packet part format: " + packet + "\n");
            return;
        }
        String sequenceId = parts[2];
        int partNumber = Integer.parseInt(parts[3]);
        int totalParts = Integer.parseInt(parts[4]);
        String imei = parts[5].substring(0, parts[5].indexOf(","));
        String data = parts[5].substring(parts[5].indexOf(",") + 1);

        packetParts.computeIfAbsent(sequenceId, k -> new HashMap<>()).put(partNumber, data);

        if (packetParts.get(sequenceId).size() == totalParts) {
            StringBuilder fullPacket = new StringBuilder();
            for (int i = 1; i <= totalParts; i++) {
                fullPacket.append(packetParts.get(sequenceId).get(i));
            }
            packetParts.remove(sequenceId);
            String reassembledPacket = fullPacket.toString();
            System.out.println("Server (port " + port + ") - Received Reassembled packet for IMEI " + imei + ": " + reassembledPacket + "\n");
//            System.out.println("Server (port " + port + ") - Size of reassembled packet for IMEI " + imei + ": " + reassembledPacket.getBytes().length + " bytes\n");
        }
    }
}