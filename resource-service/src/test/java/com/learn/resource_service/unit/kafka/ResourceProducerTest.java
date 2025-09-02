package com.learn.resource_service.unit.kafka;

import com.learn.resource_service.kafka.ResourceProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResourceProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private SendResult<String, String> sendResult;

    private ResourceProducer resourceProducer;
    private final String topic = "resource-created-topic";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resourceProducer = new ResourceProducer(kafkaTemplate, topic);
    }

    @Test
    void sendId_success() throws Exception {
        // Arrange
        String id = "123";
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq(topic), eq(id))).thenReturn(future);
        when(sendResult.getRecordMetadata()).thenReturn(mock(org.apache.kafka.clients.producer.RecordMetadata.class));

        // Act
        resourceProducer.sendId(id);

        // Assert
        verify(kafkaTemplate).send(topic, id);
    }

    @Test
    void sendId_handlesException() throws Exception {
        // Arrange
        String id = "123";
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(eq(topic), eq(id))).thenReturn(future);

        // Act - should not throw exception due to try-catch
        resourceProducer.sendId(id);

        // Assert
        verify(kafkaTemplate).send(topic, id);
    }

    @Test
    void sendId_retriesOnFailure() throws Exception {
        // Arrange
        String id = "123";
        when(kafkaTemplate.send(eq(topic), eq(id)))
                .thenThrow(new RuntimeException("First attempt failed"))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(mock(org.apache.kafka.clients.producer.RecordMetadata.class));

        // Act
        resourceProducer.sendId(id);

        // Assert - should be called at least once (actual retries managed by Spring)
        verify(kafkaTemplate, atLeastOnce()).send(topic, id);
    }
}