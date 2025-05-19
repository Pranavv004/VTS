package com.example.Server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        // Default port if not specified
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default port 8080: " + e.getMessage());
            }
        }

        // Configure SSL properties
        try {
            // Explicitly set keystore type to JKS
            System.setProperty("javax.net.ssl.keyStoreType", "JKS");
            // Load keystore from classpath (relative path in src/main/resources)
            System.setProperty("javax.net.ssl.keyStore", "server-keystore.jks");
            System.setProperty("javax.net.ssl.keyStorePassword", "password");
        } catch (Exception e) {
            System.err.println("Failed to configure SSL properties: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Start the server
        startServer(port);

        // Keep Spring Boot running
        SpringApplication.run(ServerApplication.class, args);
    }

    private static void startServer(int port) {
        try {
            // Create SSLServerSocket
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            if (factory == null) {
                throw new IllegalStateException("SSLServerSocketFactory is null");
            }
            ServerSocket serverSocket = factory.createServerSocket(port);
            System.out.println("Server started on port " + port);

            // Accept client connections in a loop
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Server (port " + port + ") - Client connected from " + clientSocket.getInetAddress());
                    handleClient(clientSocket, port);
                } catch (IOException e) {
                    System.err.println("Server (port " + port + ") - Error handling client: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Server failed to start on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket, int port) throws IOException {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {
            // Read the length prefix (4 bytes)
            int length = in.readInt();
            // Read the packet data
            byte[] packetBytes = new byte[length];
            in.readFully(packetBytes);
            String packet = new String(packetBytes);
            // Parse IMEI from packet
            String[] parts = packet.split(",");
            String imei = parts.length > 3 ? parts[3] : "Unknown";
            System.out.println("Server (port " + port + ") - Client connected, IMEI: " + imei + ", Login packet: " + packet);
        }
    }
}