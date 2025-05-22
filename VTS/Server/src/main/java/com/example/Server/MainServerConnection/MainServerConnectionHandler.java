package com.example.Server.MainServerConnection;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class MainServerConnectionHandler {
    private final int regionalPort;
    private final Consumer<Void> onRequestCurrentLocation;
    private SSLSocket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public MainServerConnectionHandler(int regionalPort, Consumer<Void> onRequestCurrentLocation) {
        this.regionalPort = regionalPort;
        this.onRequestCurrentLocation = onRequestCurrentLocation;
    }

    public void connect() {
        try {
            System.setProperty("javax.net.ssl.trustStoreType", "JKS");
            URL truststoreUrl = MainServerConnectionHandler.class.getClassLoader().getResource("client-truststore.jks");
            if (truststoreUrl == null) {
                throw new IllegalStateException("client-truststore.jks not found in classpath");
            }
            String truststorePath = URLDecoder.decode(truststoreUrl.getPath(), StandardCharsets.UTF_8);
            System.setProperty("javax.net.ssl.trustStore", truststorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", "password");

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = (SSLSocket) factory.createSocket("localhost", 8085); // Updated to match Main Server port
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            System.out.println("Regional Server (port " + regionalPort + ") - Connected to Main Server\n");

            new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        int length = in.readInt();
                        byte[] requestData = new byte[length];
                        in.readFully(requestData);
                        String request = new String(requestData);
                        if (request.equals("REQUEST_CURRENT_LOCATION")) {
                            System.out.println("Regional Server (port " + regionalPort + ") - Received REQUEST_CURRENT_LOCATION from Main Server\n");
                            onRequestCurrentLocation.accept(null);
                        } else {
                            System.out.println("Regional Server (port " + regionalPort + ") - Received unrecognized message from Main Server: " + request + "\n");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Regional Server (port " + regionalPort + ") - Error handling Main Server connection: " + e.getMessage() + "\n");
                }
            }).start();
        } catch (Exception e) {
            System.err.println("Regional Server (port " + regionalPort + ") - Failed to connect to Main Server: " + e.getMessage() + "\n");
        }
    }

    public void forwardPacket(byte[] packet) {
        try {
            out.writeInt(packet.length);
            out.write(packet);
            out.flush();
            System.out.println("Regional Server (port " + regionalPort + ") - Forwarded packet to Main Server: " + new String(packet) + "\n");
        } catch (Exception e) {
            System.err.println("Regional Server (port " + regionalPort + ") - Error forwarding packet to Main Server: " + e.getMessage() + "\n");
        }
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("Regional Server (port " + regionalPort + ") - Error closing Main Server connection: " + e.getMessage() + "\n");
        }
    }
}