package com.learn.resource_service.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class ResourceProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String resourceCreatedTopic;

    public ResourceProducer(KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${kafka.topic.resource-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.resourceCreatedTopic = topic;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sendId(String id) {
        try {
            // wait for ack from Kafka
            var result = kafkaTemplate.send(resourceCreatedTopic, id).get();

            System.out.printf(
                    "Sent ID=%s to topic=%s, partition=%d, offset=%d%n",
                    id,
                    resourceCreatedTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (Exception e) {
            System.err.printf("Failed to send ID=%s to topic=%s. Reason: %s%n",
                    id, resourceCreatedTopic, e.getMessage());
            e.printStackTrace();
        }
    }
}
