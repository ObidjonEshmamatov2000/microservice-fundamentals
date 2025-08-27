package com.learn.resource_processor.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaResourceConsumer {

    @KafkaListener(topics = "resource-created")
    public void consume(String message) {
        System.out.println("Received: " + message);
    }
}
