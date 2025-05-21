package com.example.Server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.example.Server.MainServerConnection.MainServerConnectionHandler;
import com.example.Server.PacketReciever.PacketReassembler;
import jakarta.annotation.PostConstruct;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class ServerApplication {

    private static final int PORT = Integer.parseInt(System.getProperty("server.port", "8093"));
    private final PacketReassembler packetReassembler = new PacketReassembler(PORT);
    private MainServerConnectionHandler mainServerConnectionHandler;
    // Map to store DataOutputStream for each client (by client socket)
    private final ConcurrentHashMap<Socket, DataOutputStream> clientOutputs = new ConcurrentHashMap<>();

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
            System.err.println("Failed to configure SSL properties: " + e.getMessage() + "\n");
            e.printStackTrace();
            return;
        }

        // Initialize Main Server connection
        mainServerConnectionHandler = new MainServerConnectionHandler(PORT, (Void) -> requestCurrentLocationFromClients());
        mainServerConnectionHandler.connect();

        ExecutorService clientHandlerPool = Executors.newFixedThreadPool(10);

        try {
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            System.out.println("Server started on port " + PORT + "\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Server (port " + PORT + ") - Client connected from " + clientSocket.getInetAddress() + "\n");

                clientHandlerPool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                         DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                        // Store the output stream for this client
                        clientOutputs.put(clientSocket, out);

                        // Send REQUEST_LOCATION every 10 seconds
                        new Thread(() -> {
                            try {
                                while (!clientSocket.isClosed()) {
                                    String request = "REQUEST_LOCATION";
                                    out.writeInt(request.length());
                                    out.write(request.getBytes());
                                    out.flush();
                                    System.out.println("Server (port " + PORT + ") - Sent REQUEST_LOCATION to client from " + clientSocket.getInetAddress() + "\n");
                                    Thread.sleep(10000); // 10 seconds interval
                                }
                            } catch (Exception e) {
                                System.out.println("Server (port " + PORT + ") - Error sending REQUEST_LOCATION: " + e.getMessage() + "\n");
                            }
                        }).start();

                        // Handle incoming packets
                        while (true) {
                            int length = in.readInt();
                            byte[] packetBytes = new byte[length];
                            in.readFully(packetBytes);
                            String packet = new String(packetBytes);

                            // Forward the packet to Main Server
                            mainServerConnectionHandler.forwardPacket(packetBytes);

                            if (packet.startsWith("$,PART,")) {
                                packetReassembler.handlePacketPart(packet);
                            } else if (packet.startsWith("$,LG,")) {
                                String[] parts = packet.split(",");
                                String imei = parts.length > 3 ? parts[3] : "Unknown";
                                System.out.println("Server (port " + PORT + ") - IMEI: " + imei + ", Login packet: " + packet + "\n");
                            } else {
                                String[] parts = packet.split(",");
                                String imei = parts.length > 7 ? parts[7] : "Unknown";
                                System.out.println("Server (port " + PORT + ") - IMEI: " + imei + ", Location packet: " + packet + "\n");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Server (port " + PORT + ") - Connection Closed: " + e.getMessage() + "\n");
                    } finally {
                        clientOutputs.remove(clientSocket);
                        try {
                            clientSocket.close();
                        } catch (Exception ex) {
                            System.err.println("Error closing client socket: " + ex.getMessage() + "\n");
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Server failed on port " + PORT + ": " + e.getMessage() + "\n");
        } finally {
            clientHandlerPool.shutdown();
            mainServerConnectionHandler.close();
        }
    }

    private void requestCurrentLocationFromClients() {
        clientOutputs.forEach((clientSocket, out) -> {
            try {
                String request = "REQUEST_LOCATION";
                out.writeInt(request.length());
                out.write(request.getBytes());
                out.flush();
                System.out.println("Server (port " + PORT + ") - Sent REQUEST_LOCATION to client " + clientSocket.getInetAddress() + " upon Main Server request\n");
            } catch (Exception e) {
                System.out.println("Server (port " + PORT + ") - Error sending REQUEST_LOCATION to client: " + e.getMessage() + "\n");
            }
        });
    }
}