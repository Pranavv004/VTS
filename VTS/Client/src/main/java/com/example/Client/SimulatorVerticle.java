package com.example.Client;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;

public class SimulatorVerticle extends AbstractVerticle {
    @Override
    public void start() {
        int numberOfSockets = 2; // 2 sockets, each with 5 virtual clients
        int[] serverPorts = {8081, 8082}; // Ports for 2 regional servers

        for (int i = 0; i < numberOfSockets; i++) {
            final int port = serverPorts[i];
            final int verticleIndex = i + 1; // Capture index as final variable
            vertx.deployVerticle(() -> new ClientVerticle(port), new DeploymentOptions(), res -> {
                if (res.succeeded()) {
                    System.out.println("Deployed client verticle " + verticleIndex + " to port " + port + " with 5 virtual clients");
                } else {
                    System.err.println("Failed to deploy client verticle to port " + port + ": " + res.cause().getMessage());
                }
            });
            try {
                Thread.sleep(100); // Delay to manage deployment load
            } catch (InterruptedException e) {
                System.err.println("Interrupted during deployment: " + e.getMessage());
            }
        }
    }
}