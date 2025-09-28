package com.example.kafkamiddleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class KafkaMiddlewareApplication {
    public static void main(String[] args) {
        SpringApplication.run(KafkaMiddlewareApplication.class, args);
    }
}
