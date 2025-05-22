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
import java.io.EOFException;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
public class ServerApplication {

    private static final int PORT = Integer.parseInt(System.getProperty("server.port", "8094"));
    private MainServerConnectionHandler mainServerConnectionHandler;
    private PacketReassembler packetReassembler;
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

        mainServerConnectionHandler = new MainServerConnectionHandler(PORT, (Void) -> requestCurrentLocationFromClients());
        mainServerConnectionHandler.connect();

        packetReassembler = new PacketReassembler(PORT, mainServerConnectionHandler);

        ExecutorService clientHandlerPool = Executors.newFixedThreadPool(10);
        SSLServerSocket serverSocket = null;

        try {
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            System.out.println("Server started on port " + PORT + "\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Server (port " + PORT + ") - Client connected from " + clientSocket.getInetAddress() + "\n");

                clientHandlerPool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                         DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                        clientOutputs.put(clientSocket, out);

                        while (!clientSocket.isClosed()) {
                            int length = in.readInt();
                            if (length <= 0) {
                                System.out.println("Server (port " + PORT + ") - Received invalid packet length from client " + clientSocket.getInetAddress() + "\n");
                                break;
                            }
                            byte[] packetBytes = new byte[length];
                            in.readFully(packetBytes);
                            String packet = new String(packetBytes);
                            //System.out.println("Server (port " + PORT + ") - Received packet: " + packet + "\n");

                            if (packet.startsWith("$,PART,")) {
                                packetReassembler.handlePacketPart(packet);
                            } else if (packet.startsWith("$,LG,")) {
                                String[] parts = packet.split(",");
                                String imei = parts.length > 3 ? parts[3] : "Unknown";
                                //System.out.println("Server (port " + PORT + ") - IMEI: " + imei + ", Send Login packet: " + packet + "\n");
                                mainServerConnectionHandler.forwardPacket(packetBytes);
                            } else {
                                String[] parts = packet.split(",");
                                String imei = parts.length > 7 ? parts[7] : "Unknown";
                                //System.out.println("Server (port " + PORT + ") - IMEI: " + imei + ", Send Location packet: " + packet + "\n");
                                mainServerConnectionHandler.forwardPacket(packetBytes);
                            }
                        }
                    } catch (EOFException e) {
                        System.out.println("Server (port " + PORT + ") - Client " + clientSocket.getInetAddress() + " disconnected gracefully\n");
                    } catch (Exception e) {
                        System.out.println("Server (port " + PORT + ") - Error with client " + clientSocket.getInetAddress() + ": " + e.getMessage() + "\n");
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
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing server socket: " + e.getMessage() + "\n");
            }
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