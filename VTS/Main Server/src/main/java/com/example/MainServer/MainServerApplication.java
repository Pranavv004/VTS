package com.example.MainServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class MainServerApplication {
    private static final int PORT = 8097; 
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
        SSLServerSocket serverSocket = null;

        try {
            SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            serverSocket = (SSLServerSocket) factory.createServerSocket(PORT);
            System.out.println("Main Server started on port " + PORT + "\n");

            while (true) {
                Socket regionalSocket = serverSocket.accept();
                int regionalPort = regionalSocket.getPort();
                System.out.println("Main Server (port " + PORT + ") - Regional Server connected from " + regionalSocket.getInetAddress() + ":" + regionalPort + "\n");

                regionalHandlerPool.submit(() -> {
                    try (DataInputStream in = new DataInputStream(regionalSocket.getInputStream());
                         DataOutputStream out = new DataOutputStream(regionalSocket.getOutputStream())) {
                        regionalServerOutputs.put(regionalPort, out);

                        new Thread(() -> {
                            try {
                                while (!regionalSocket.isClosed()) {
                                    String request = "REQUEST_CURRENT_LOCATION";
                                    out.writeInt(request.length());
                                    out.write(request.getBytes());
                                    out.flush();
                                    System.out.println("Main Server (port " + PORT + ") - Sent REQUEST_CURRENT_LOCATION to Regional Server at " + regionalSocket.getInetAddress() + ":" + regionalPort + "\n");
                                    Thread.sleep(30000);
                                }
                            } catch (Exception e) {
                                System.out.println("Main Server (port " + PORT + ") - Error sending REQUEST_CURRENT_LOCATION to Regional Server at port " + regionalPort + ": " + e.getMessage() + "\n");
                            }
                        }).start();

                        while (!regionalSocket.isClosed()) {
                            int length = in.readInt();
                            if (length <= 0) {
                                System.out.println("Main Server (port " + PORT + ") - Received invalid packet length from Regional Server on port " + regionalPort + "\n");
                                break;
                            }
                            byte[] packetBytes = new byte[length];
                            in.readFully(packetBytes);
                            String packet = new String(packetBytes);
                            System.out.println("Main Server (port " + PORT + ") - Received packet from Regional Server at " + regionalSocket.getInetAddress() + ":" + regionalPort + ": " + packet + "\n");
                        }
                    } catch (EOFException e) {
                        System.out.println("Main Server (port " + PORT + ") - Regional Server at port " + regionalPort + " disconnected gracefully\n");
                    } catch (Exception e) {
                        System.out.println("Main Server (port " + PORT + ") - Error with Regional Server at port " + regionalPort + ": " + e.getMessage() + "\n");
                    } finally {
                        regionalServerOutputs.remove(regionalPort);
                        try {
                            regionalSocket.close();
                        } catch (Exception ex) {
                            System.err.println("Main Server (port " + PORT + ") - Error closing Regional Server socket for port " + regionalPort + ": " + ex.getMessage() + "\n");
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Main Server failed on port " + PORT + ": " + e.getMessage() + "\n");
        } finally {
            regionalHandlerPool.shutdown();
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (Exception e) {
                System.err.println("Main Server (port " + PORT + ") - Error closing server socket: " + e.getMessage() + "\n");
            }
        }
    }
}