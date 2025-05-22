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
    private static final int MAIN_SERVER_PORT = 8099;
    private final int regionalPort;
    private SSLSocket mainServerSocket;
    private DataOutputStream mainServerOut;
    private DataInputStream mainServerIn;
    private final Consumer<Void> onRequestCurrentLocation;

    public MainServerConnectionHandler(int regionalPort, Consumer<Void> onRequestCurrentLocation) {
        this.regionalPort = regionalPort;
        this.onRequestCurrentLocation = onRequestCurrentLocation;
    }

    public void connect() {
        try {
            // Configure truststore for connecting to Main Server as a client
            System.setProperty("javax.net.ssl.trustStoreType", "JKS");
            URL truststoreUrl = MainServerConnectionHandler.class.getClassLoader().getResource("client-truststore.jks");
            if (truststoreUrl == null) {
                throw new IllegalStateException("client-truststore.jks not found in classpath");
            }
            String truststorePath = URLDecoder.decode(truststoreUrl.getPath(), StandardCharsets.UTF_8);
            System.setProperty("javax.net.ssl.trustStore", truststorePath);
            System.setProperty("javax.net.ssl.trustStorePassword", "password");

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            mainServerSocket = (SSLSocket) factory.createSocket("localhost", MAIN_SERVER_PORT);
            mainServerOut = new DataOutputStream(mainServerSocket.getOutputStream());
            mainServerIn = new DataInputStream(mainServerSocket.getInputStream());
            //System.out.println("Regional Server (port " + regionalPort + ") - Connected to Main Server on port " + MAIN_SERVER_PORT + "\n");

            // Start a thread to listen for requests from Main Server
            new Thread(() -> {
                try {
                    while (!mainServerSocket.isClosed()) {
                        int length = mainServerIn.readInt();
                        byte[] requestData = new byte[length];
                        mainServerIn.readFully(requestData);
                        String request = new String(requestData);
                        if (request.equals("REQUEST_CURRENT_LOCATION")) {
                            System.out.println("Regional Server (port " + regionalPort + ") - Received REQUEST_CURRENT_LOCATION from Main Server\n");
                            onRequestCurrentLocation.accept(null);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Regional Server (port " + regionalPort + ") - Main Server connection closed: " + e.getMessage() + "\n");
                }
            }).start();
        } catch (Exception e) {
            System.err.println("Regional Server (port " + regionalPort + ") - Failed to connect to Main Server: " + e.getMessage() + "\n");
        }
    }

    public void forwardPacket(byte[] packetBytes) {
        try {
            if (mainServerOut != null) {
                mainServerOut.writeInt(packetBytes.length);
                mainServerOut.write(packetBytes);
                mainServerOut.flush();
            }
        } catch (Exception e) {
            System.err.println("Regional Server (port " + regionalPort + ") - Error forwarding packet to Main Server: " + e.getMessage() + "\n");
        }
    }

    public void close() {
        try {
            if (mainServerSocket != null) {
                mainServerSocket.close();
            }
        } catch (Exception e) {
            System.err.println("Regional Server (port " + regionalPort + ") - Error closing Main Server connection: " + e.getMessage() + "\n");
        }
    }
}