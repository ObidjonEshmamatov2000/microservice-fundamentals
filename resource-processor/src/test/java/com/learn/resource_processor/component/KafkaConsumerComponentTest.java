package com.learn.resource_processor.component;

import com.learn.resource_processor.service.ResourceProcessorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"resource-created"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.LongDeserializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.LongSerializer"
})
@DirtiesContext
class KafkaConsumerComponentTest {

    @Autowired
    private KafkaTemplate<String, Long> kafkaTemplate;

    @MockitoBean
    private ResourceProcessorService resourceProcessorService;

    @Test
    @DisplayName("Should consume message from Kafka and process resource")
    void shouldConsumeMessageAndProcessResource() throws InterruptedException {
        // Given
        Long resourceId = 12345L;
        String topic = "resource-created";

        // When
        kafkaTemplate.send(topic, resourceId);

        // Then
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    verify(resourceProcessorService).process(resourceId);
                });
    }

    @Test
    @DisplayName("Should handle multiple messages from Kafka")
    void shouldHandleMultipleMessages() throws InterruptedException {
        // Given
        List<Long> resourceIds = Arrays.asList(111L, 222L, 333L);
        String topic = "resource-created";

        // When
        resourceIds.forEach(id -> kafkaTemplate.send(topic, id));

        // Then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    resourceIds.forEach(id ->
                            verify(resourceProcessorService).process(id));
                });
    }
}
