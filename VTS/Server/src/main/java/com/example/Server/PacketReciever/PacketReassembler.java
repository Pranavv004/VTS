package com.example.Server.PacketReciever;

import com.example.Server.MainServerConnection.MainServerConnectionHandler;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class PacketReassembler {
    private final int port;
    private final Map<String, TreeMap<Integer, String>> packetPartsBySequence = new ConcurrentHashMap<>();
    private final Map<String, PacketMetadata> metadataBySequence = new ConcurrentHashMap<>();
    private final MainServerConnectionHandler mainServerConnectionHandler;

    private static class PacketMetadata {
        String imei;
        int totalParts;

        PacketMetadata(String imei, int totalParts) {
            this.imei = imei;
            this.totalParts = totalParts;
        }
    }

    public PacketReassembler(int port, MainServerConnectionHandler mainServerConnectionHandler) {
        this.port = port;
        this.mainServerConnectionHandler = mainServerConnectionHandler;
    }

    public void handlePacketPart(String packet) {
        //System.out.println("Server (port " + port + ") - Received packet part: " + packet + "\n");
        String[] parts = packet.split(",", 7); // Split only up to the header ($,PART,sequenceId,partNumber,totalParts,imei,)
        if (parts.length < 7) {
            System.out.println("Server (port " + port + ") - Invalid packet part: " + packet + "\n");
            return;
        }

        String sequenceId = parts[2];
        int partNumber = Integer.parseInt(parts[3]);
        int totalParts = Integer.parseInt(parts[4]);
        String imei = parts[5];

        // Extract the data part (everything after the 6th comma)
        int headerEndIndex = packet.indexOf(parts[5]) + parts[5].length() + 1; // Position after imei and comma
        String dataPart = packet.substring(headerEndIndex);

        metadataBySequence.computeIfAbsent(sequenceId, k -> new PacketMetadata(imei, totalParts));

        packetPartsBySequence.computeIfAbsent(sequenceId, k -> new TreeMap<>()).put(partNumber, dataPart);
        //System.out.println("Server (port " + port + ") - Stored part " + partNumber + " of " + totalParts + " for IMEI " + imei + " (Sequence ID: " + sequenceId + ")\n");

        TreeMap<Integer, String> partsMap = packetPartsBySequence.get(sequenceId);
        if (partsMap.size() == totalParts) {
            StringBuilder reassembledPacket = new StringBuilder();
            partsMap.values().forEach(reassembledPacket::append);
            String finalPacket = reassembledPacket.toString();
            //System.out.println("Server (port " + port + ") - Size of reassembled packet for IMEI " + imei + ": " + finalPacket.length() + " bytes\n");
            System.out.println("Server (port " + port + ") - Reassembled packet for IMEI " + imei + ": " + finalPacket + "\n");
            mainServerConnectionHandler.forwardPacket(finalPacket.getBytes());


            packetPartsBySequence.remove(sequenceId);
            metadataBySequence.remove(sequenceId);
        }
    }
}