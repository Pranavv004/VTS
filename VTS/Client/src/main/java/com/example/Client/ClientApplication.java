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
        Vertx vertx = Vertx.vertx();

        SimulatorVerticle simulatorVerticle = new SimulatorVerticle();  //manages the deployment of ClientVerticle instances
        
        vertx.deployVerticle(simulatorVerticle, res -> { 		// Deploy the verticle
            if (res.succeeded()) {
                System.out.println("SimulatorVerticle deployed successfully");
            } else {
                System.err.println("Failed to deploy SimulatorVerticle: " + res.cause().getMessage());
            }
        });
    }
}