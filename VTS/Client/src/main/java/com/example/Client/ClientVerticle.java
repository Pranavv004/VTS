package com.example.Client;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;

import java.util.ArrayList;
import java.util.List;

public class ClientVerticle extends AbstractVerticle {
    private NetSocket socket;
    private final List<Client> virtualClients = new ArrayList<>();
    private final int serverPort;

    // Client data structure
    private static class Client {
        String vehicleRegNo;
        String imei;
        double firmwareVersion;
        int protocolIdentifier;
        double latitude;
        String latitudeDir;
        double longitude;
        String longitudeDir;

        Client(String vehicleRegNo, String imei, double firmwareVersion, int protocolIdentifier,
               double latitude, String latitudeDir, double longitude, String longitudeDir) {
            this.vehicleRegNo = vehicleRegNo;
            this.imei = imei;
            this.firmwareVersion = firmwareVersion;
            this.protocolIdentifier = protocolIdentifier;
            this.latitude = latitude;
            this.latitudeDir = latitudeDir;
            this.longitude = longitude;
            this.longitudeDir = longitudeDir;
        }

        String toLoginPacket() {
            return String.format("$,LG,%s,%s,%.3f,%d,%.6f,%s,%.6f,%s",
                    vehicleRegNo, imei, firmwareVersion, protocolIdentifier,
                    latitude, latitudeDir, longitude, longitudeDir);
        }
    }

    public ClientVerticle() {
        this.serverPort = 8080; // Default port
    }

    public ClientVerticle(int serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public void start() {
        int virtualClientCount = 5; // 5 virtual clients per socket
        NetClientOptions options = new NetClientOptions()
                .setSsl(true)
                .setTrustStoreOptions(new io.vertx.core.net.JksOptions()
                        .setPath("client-truststore.jks")
                        .setPassword("password"))
                .setHostnameVerificationAlgorithm("HTTPS") // Enable hostname verification
                .setConnectTimeout(10000);

        NetClient client = vertx.createNetClient(options);

        // Initialize virtual clients with unique IMEIs
        for (int i = 0; i < virtualClientCount; i++) {
            String imei = "869523059602" + String.format("%03d", (getVerticleIndex() * virtualClientCount) + i);
            virtualClients.add(new Client(
                    "KL23H6667", // Vehicle Reg. No
                    imei,        // Unique IMEI
                    6.102,       // Firmware Version
                    1,           // Protocol Identifier
                    9.095031,    // Latitude
                    "N",         // Latitude Dir
                    76.495429,   // Longitude
                    "E"          // Longitude Dir
            ));
        }

        // Connect to regional server
        client.connect(serverPort, "localhost", res -> {
            if (res.succeeded()) {
                socket = res.result();
                System.out.println("Socket connected to port " + serverPort + " for " + virtualClientCount + " virtual clients");
                sendLoginPackets();
            } else {
                System.err.println("Connection failed to port " + serverPort + ": " + res.cause().getMessage());
            }
        });
    }

    private void sendLoginPackets() {
        virtualClients.forEach(client -> {
            String packet = client.toLoginPacket();
            sendPacket(packet);
            System.out.println("Sent login packet to port " + serverPort + ": " + packet);
        });
    }

    private void sendPacket(String packet) {
        if (socket != null) {
            Buffer buffer = Buffer.buffer();
            byte[] data = packet.getBytes();
            buffer.appendInt(data.length); // Length prefix
            buffer.appendBytes(data);
            socket.write(buffer);
        }
    }

    @Override
    public void stop() {
        if (socket != null) {
            socket.close();
            System.out.println("Socket disconnected from port " + serverPort + " for " + virtualClients.size() + " virtual clients");
        }
    }

    private int getVerticleIndex() {
        return serverPort == 8080 ? 0 : 1;
    }
}