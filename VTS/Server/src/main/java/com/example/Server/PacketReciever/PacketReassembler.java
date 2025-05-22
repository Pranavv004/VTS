package com.example.Server.PacketReciever;

import com.example.Server.MainServerConnection.MainServerConnectionHandler;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class PacketReassembler {
    private final int port;
    private final Map<String, TreeMap<Integer, String>> packetParts = new ConcurrentHashMap<>();
    private final MainServerConnectionHandler mainServerConnectionHandler;

    public PacketReassembler(int port, MainServerConnectionHandler mainServerConnectionHandler) {
        this.port = port;
        this.mainServerConnectionHandler = mainServerConnectionHandler;
    }

    public void handlePacketPart(String packet) {
        String[] parts = packet.split(",");
        if (parts.length < 6) {
            System.out.println("Server (port " + port + ") - Invalid packet part: " + packet + "\n");
            return;
        }
        int partNumber = Integer.parseInt(parts[2]);
        int totalParts = Integer.parseInt(parts[3]);
        String imei = parts[5];

        String[] headerParts = packet.split("\\*");
        if (headerParts.length < 2) {
            System.out.println("Server (port " + port + ") - Invalid packet part format: " + packet + "\n");
            return;
        }
        String dataPart = headerParts[1];
        packetParts.computeIfAbsent(imei, k -> new TreeMap<>()).put(partNumber, dataPart);

        if (packetParts.get(imei).size() == totalParts) {
            StringBuilder reassembledPacket = new StringBuilder();
            packetParts.get(imei).values().forEach(reassembledPacket::append);
            String finalPacket = reassembledPacket.toString();
            System.out.println("Server (port " + port + ") - Reassembled packet for IMEI " + imei + ": " + finalPacket + "\n");
            System.out.println("Server (port " + port + ") - Size of reassembled packet for IMEI " + imei + ": " + finalPacket.length() + " bytes\n");

            // Forward the reassembled packet to the Main Server
            mainServerConnectionHandler.forwardPacket(finalPacket.getBytes());

            packetParts.remove(imei);
        }
    }
}