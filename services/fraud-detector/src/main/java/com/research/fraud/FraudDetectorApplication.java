package com.research.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication
@EnableWebSocket
public class FraudDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectorApplication.class, args);
    }

}
