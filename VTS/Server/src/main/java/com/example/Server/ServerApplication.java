package com.example.Server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.DataInputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class ServerApplication {

    private static final int PORT = Integer.parseInt(System.getProperty("server.port", "8080"));

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    @PostConstruct
    public void startServer() {
        try {
            System.setProperty("javax.net.ssl.keyStoreType", "JKS");
            URL keystoreUrl = ServerApplication.class.getClassLoader().getResource("server-keystore.jks");
            if (keystoreUrl == null) {
                throw new IllegalStateException("server-keystore.jks not found in classpath");
            }
            String keystorePath = URLDecoder.decode(keystoreUrl.getPath(), StandardCharsets.UTF_8);
            System.setProperty("javax.net.ssl.keyStore", keystorePath);
            System.setProperty("javax.net.ssl.keyStorePassword", "password");
        } catch (Exception e) {
            System.err.println("Failed to configure SSL properties: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        ExecutorService clientHandlerPool = Executors.newFixedThreadPool(10);

        try {
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Server (port " + PORT + ") - Client connected from " + clientSocket.getInetAddress());

                clientHandlerPool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {
                        while (true) {
                            int length = in.readInt();
                            byte[] packetBytes = new byte[length];
                            in.readFully(packetBytes);
                            String packet = new String(packetBytes);
                            String[] parts = packet.split(",");
                            String imei = parts.length > 3 ? parts[3] : "Unknown";
                            System.out.println("Server (port " + PORT + ") -  IMEI: " + imei + ", Login packet: " + packet);
                        }
                    } catch (Exception e) {
                        System.out.println("Server (port " + PORT + ") - Connection Closed" + e.getMessage());
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (Exception ex) {
                            System.err.println("Error closing client socket: " + ex.getMessage());
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Server failed on port " + PORT + ": " + e.getMessage());
        } finally {
            clientHandlerPool.shutdown();
        }
    }
}