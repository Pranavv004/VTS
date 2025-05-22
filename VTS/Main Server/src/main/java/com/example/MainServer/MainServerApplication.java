package com.example.MainServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class MainServerApplication {
    private static final int PORT = 8099;
    // Map to store DataOutputStream for each Regional Server (by port)
    private final ConcurrentHashMap<Integer, DataOutputStream> regionalServerOutputs = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SpringApplication.run(MainServerApplication.class, args);
    }

    @PostConstruct
    public void startServer() {
        try {
            System.setProperty("javax.net.ssl.keyStoreType", "JKS");
            URL keystoreUrl = MainServerApplication.class.getClassLoader().getResource("server-keystore.jks");
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

        ExecutorService regionalHandlerPool = Executors.newFixedThreadPool(10);

        try {
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            System.out.println("Main Server started on port " + PORT + "\n");

            while (true) {
                Socket regionalSocket = serverSocket.accept();
                int regionalPort = regionalSocket.getPort();
                System.out.println("Main Server (port " + PORT + ") - Regional Server connected from " + regionalSocket.getInetAddress() + ":" + regionalPort + "\n");

                regionalHandlerPool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(regionalSocket.getInputStream());
                         DataOutputStream out = new DataOutputStream(regionalSocket.getOutputStream())) {
                        // Store the output stream for this Regional Server
                        regionalServerOutputs.put(regionalPort, out);

                        // Send REQUEST_LOCATION to Regional Server every 15 seconds
                        new Thread(() -> {
                            try {
                                while (!regionalSocket.isClosed()) {
                                    String request = "REQUEST_CURRENT_LOCATION";
                                    out.writeInt(request.length());
                                    out.write(request.getBytes());
                                    out.flush();
                                    System.out.println("\nSent REQUEST_CURRENT_LOCATION to Regional Server at " + regionalSocket.getInetAddress() + ":" + regionalPort + "\n");
                                    Thread.sleep(30000); // 15 seconds interval
                                }
                            } catch (Exception e) {
                                System.out.println("Main Server (port " + PORT + ") - Error sending REQUEST_CURRENT_LOCATION: " + e.getMessage() + "\n");
                            }
                        }).start();

                        // Handle incoming packets from Regional Server
                        while (true) {
                            int length = in.readInt();
                            byte[] packetBytes = new byte[length];
                            in.readFully(packetBytes);
                            String packet = new String(packetBytes);
                            System.out.println("Received packet from Regional Server at " + regionalSocket.getInetAddress() + ":" + regionalPort + ": " + packet + "\n");
                        }
                    } catch (Exception e) {
                        System.out.println("\nConnection Closed for Regional Server at port " + regionalPort + ": " + e.getMessage() + "\n");
                    } finally {
                        regionalServerOutputs.remove(regionalPort);
                        try {
                            regionalSocket.close();
                        } catch (Exception ex) {
                            System.err.println("Error closing regional socket: " + ex.getMessage() + "\n");
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Main Server failed on port " + PORT + ": " + e.getMessage() + "\n");
        } finally {
            regionalHandlerPool.shutdown();
        }
    }

}