package com.example.Client;

import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @PostConstruct
    public void deployVerticle() {
        // Create Vertx instance manually
        Vertx vertx = Vertx.vertx();
        // Create SimulatorVerticle instance manually
        SimulatorVerticle simulatorVerticle = new SimulatorVerticle();
        // Deploy the verticle
        vertx.deployVerticle(simulatorVerticle, res -> {
            if (res.succeeded()) {
                System.out.println("SimulatorVerticle deployed successfully");
            } else {
                System.err.println("Failed to deploy SimulatorVerticle: " + res.cause().getMessage());
            }
        });
    }
}