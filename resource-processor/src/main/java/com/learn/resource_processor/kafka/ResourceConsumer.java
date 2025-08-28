package com.learn.resource_processor.kafka;

import com.learn.resource_processor.service.ResourceProcessorService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ResourceConsumer {

    private final ResourceProcessorService resourceProcessorService;

    public ResourceConsumer(ResourceProcessorService resourceProcessorService) {
        this.resourceProcessorService = resourceProcessorService;
    }

    @KafkaListener(topics = "resource-created", groupId = "resource-processor-group")
    public void consume(String resourceId) {
        System.out.println("Received: " + resourceId);
        resourceProcessorService.process(resourceId);
    }
}
