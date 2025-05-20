package com.example.Client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @PostConstruct
    public void startClients() {
        // Rest of the code remains the same
        try {
            System.setProperty("javax.net.ssl.trustStoreType", "JKS");
            URL truststoreUrl = ClientApplication.class.getClassLoader().getResource("client-truststore.jks");
            if (truststoreUrl == null) {
                throw new IllegalStateException("client-truststore.jks not found in classpath");
            }
            String truststorePath = URLDecoder.decode(truststoreUrl.getPath(), StandardCharsets.UTF_8);
            System.setProperty("javax.net.ssl.trustStore", truststorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", "password");
        } catch (Exception e) {
            System.err.println("Failed to configure SSL properties: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Simulate 10 clients (5 per server)
        int clientsPerServer = 5;
        int[] serverPorts = {8085, 8086};

        for (int port : serverPorts) {
            for (int i = 0; i < clientsPerServer; i++) {
                int clientIndex = (port == 8085 ? 0 : 1) * clientsPerServer + i;
                String imei = "869523059602" + String.format("%03d", clientIndex);

                new Thread(() -> {
                    try {
                        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                        SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port);
                        System.out.println("Client " + imei + " connected to port " + port);

                        String packet = String.format("$,LG,KL23H6667,%s,6.102,1,9.095031,N,76.495429,E", imei);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        byte[] data = packet.getBytes();
                        out.writeInt(data.length);
                        out.write(data);
                        out.flush();
                        System.out.println("Client " + imei + " sent login packet to port " + port + ": " + packet);

                        Thread.sleep(5000);

                        socket.close();
                        System.out.println("Client " + imei + " closed connection to port " + port);
                    } catch (Exception e) {
                        System.err.println("Error for client " + imei + " on port " + port + ": " + e.getMessage());
                    }
                }).start();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted during client setup: " + e.getMessage());
                }
            }
        }
    }
}