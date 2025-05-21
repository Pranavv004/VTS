package com.example.Client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.Client.Handling.PacketHandler;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataInputStream;
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
        try {
            System.setProperty("javax.net.ssl.trustStoreType", "JKS");
            URL truststoreUrl = ClientApplication.class.getClassLoader().getResource("client-truststore.jks");
            if (truststoreUrl == null) {
                throw new IllegalStateException("client-truststore.jks not found in classpath");
            }
            String truststorePath = URLDecoder.decode(truststoreUrl.getPath(), StandardCharsets.UTF_8);
            System.out.println("Decoded truststore path: " + truststorePath + "\n");
            System.setProperty("javax.net.ssl.trustStore", truststorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", "password");
        } catch (Exception e) {
            System.err.println("Failed to configure SSL properties: " + e.getMessage() + "\n");
            e.printStackTrace();
            return;
        }

        int clientsPerServer = 5;
        int[] serverPorts = {8091, 8092};

        for (int port : serverPorts) {
            for (int i = 0; i < clientsPerServer; i++) {
                int clientIndex = (port == 8095 ? 0 : 1) * clientsPerServer + i;
                String imei = "869523059602" + String.format("%03d", clientIndex);

                new Thread(() -> {
                    try {
                        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                        SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port);
                        System.out.println("Client " + imei + " connected to port " + port + "\n");

                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        DataInputStream in = new DataInputStream(socket.getInputStream());

                        String packet = String.format("$,LG,KL23H6667,%s,6.102,1,9.095031,N,76.495429,E", imei);
                        byte[] data = packet.getBytes();
                        out.writeInt(data.length);
                        out.write(data);
                        out.flush();
                        System.out.println("Client " + imei + " sent login packet to port " + port + ": " + packet + "\n");

                        while (!socket.isClosed()) {
                            int length = in.readInt();
                            byte[] requestData = new byte[length];
                            in.readFully(requestData);
                            String request = new String(requestData);
                            if (request.equals("REQUEST_LOCATION")) {
                                PacketHandler.sendLocationPacket(imei, out, port);
                            }
                        }

                        socket.close();
                        System.out.println("Client " + imei + " closed connection to port " + port + "\n");
                    } catch (Exception e) {
                        System.err.println("Error for client " + imei + " on port " + port + ": " + e.getMessage() + "\n");
                    }
                }).start();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted during client setup: " + e.getMessage() + "\n");
                }
            }
        }
    }
}