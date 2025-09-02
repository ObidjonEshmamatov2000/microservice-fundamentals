package com.learn.resource_processor.integration;

import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import com.learn.resource_processor.kafka.ResourceConsumer;
import com.learn.resource_processor.service.ResourceProcessorService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9093",
                "port=9093"
        },
        topics = {"resource-created"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=test-resource-processor-group"
})
@DirtiesContext
class KafkaConsumerIntegrationTest {

    @MockitoSpyBean
    private ResourceConsumer resourceConsumer;

    @MockitoBean
    private ResourceProcessorService resourceProcessorService;

    @MockitoBean
    private ResourceServiceClient resourceServiceClient;

    @MockitoBean
    private SongServiceClient songServiceClient;

    private KafkaProducer<String, String> testProducer;

    private static final String TOPIC_NAME = "resource-created";
    private static final String TEST_RESOURCE_ID = "12345";

    @BeforeEach
    void setUp() {
        // Create test producer
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(
                System.getProperty("spring.embedded.kafka.brokers")
        );
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        testProducer = new KafkaProducer<>(producerProps);

        // Reset mocks
        reset(resourceConsumer, resourceProcessorService, resourceServiceClient, songServiceClient);
    }

    @Test
    @Timeout(10)
    void shouldConsumeResourceIdMessage() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(TEST_RESOURCE_ID);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be consumed within 5 seconds");

        verify(resourceConsumer, times(1)).consume(TEST_RESOURCE_ID);
        verify(resourceProcessorService, times(1)).process(TEST_RESOURCE_ID);
    }

    @Test
    @Timeout(10)
    void shouldConsumeMultipleMessages() throws InterruptedException {
        // Given
        List<String> resourceIds = Arrays.asList("101", "102", "103");
        CountDownLatch latch = new CountDownLatch(resourceIds.size());

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        for (String resourceId : resourceIds) {
            sendMessageToKafka(resourceId);
        }

        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "All messages should be consumed within 10 seconds");

        verify(resourceConsumer, times(3)).consume(anyString());
        verify(resourceProcessorService, times(3)).process(anyString());

        // Verify each specific resource ID was processed
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(resourceProcessorService, times(3)).process(captor.capture());

        List<String> processedIds = captor.getAllValues();
        assertThat(processedIds).containsExactlyInAnyOrderElementsOf(resourceIds);
    }

    @Test
    @Timeout(10)
    void shouldHandleNumericResourceIds() throws InterruptedException {
        // Given
        String numericResourceId = "999999";
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(numericResourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(numericResourceId);
        verify(resourceProcessorService, times(1)).process(numericResourceId);
    }

    @Test
    @Timeout(10)
    void shouldHandleProcessingException() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            throw new RuntimeException("Processing failed");
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(TEST_RESOURCE_ID);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(TEST_RESOURCE_ID);
        verify(resourceProcessorService, times(1)).process(TEST_RESOURCE_ID);

        // The consumer should have attempted processing despite the exception
        verifyNoMoreInteractions(resourceProcessorService);
    }

    @Test
    @Timeout(10)
    void shouldConsumeEmptyMessage() throws InterruptedException {
        // Given
        String emptyResourceId = "";
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(emptyResourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(emptyResourceId);
        verify(resourceProcessorService, times(1)).process(emptyResourceId);
    }

    @Test
    @Timeout(10)
    void shouldConsumeLargeResourceId() throws InterruptedException {
        // Given
        String largeResourceId = "A".repeat(1000);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(largeResourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(largeResourceId);
        verify(resourceProcessorService, times(1)).process(largeResourceId);
    }

    @Test
    @Timeout(15)
    void shouldHandleHighThroughput() throws InterruptedException {
        // Given
        int messageCount = 100;
        CountDownLatch latch = new CountDownLatch(messageCount);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        for (int i = 0; i < messageCount; i++) {
            sendMessageToKafka("resource-" + i);
        }

        // Then
        assertTrue(latch.await(15, TimeUnit.SECONDS),
                "All " + messageCount + " messages should be consumed within 15 seconds");

        verify(resourceConsumer, times(messageCount)).consume(anyString());
        verify(resourceProcessorService, times(messageCount)).process(anyString());
    }

    @Test
    @Timeout(10)
    void shouldMaintainMessageOrder() throws InterruptedException {
        // Given
        List<String> resourceIds = Arrays.asList("order-1", "order-2", "order-3", "order-4", "order-5");
        CountDownLatch latch = new CountDownLatch(resourceIds.size());

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When - Send all messages with same key to ensure same partition and ordering
        for (String resourceId : resourceIds) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC_NAME, "same-key", resourceId
            );
            testProducer.send(record);
        }
        testProducer.flush();

        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(resourceProcessorService, times(5)).process(captor.capture());

        List<String> processedIds = captor.getAllValues();
        // Since we used the same partition, order should be maintained
        assertThat(processedIds).containsExactlyElementsOf(resourceIds);
    }

    @Test
    @Timeout(10)
    void shouldVerifyConsumerGroupConfiguration() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(TEST_RESOURCE_ID);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // The fact that the message was consumed confirms the consumer group is working
        verify(resourceConsumer, times(1)).consume(TEST_RESOURCE_ID);
    }

    @Test
    @Timeout(10)
    void shouldHandleSpecialCharactersInResourceId() throws InterruptedException {
        // Given
        String specialResourceId = "resource-123!@#$%^&*()_+-={}[]|\\:;\"'<>?,./";
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(specialResourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(specialResourceId);
        verify(resourceProcessorService, times(1)).process(specialResourceId);
    }

    @Test
    @Timeout(10)
    void shouldVerifyMessageConsumptionWithMockingChain() throws InterruptedException {
        // Given
        String resourceId = "chain-test-123";
        byte[] mockResourceData = "mock-mp3-data".getBytes();
        SongDTO mockSongDTO = new SongDTO();
        mockSongDTO.setName("Test Song");

        CountDownLatch latch = new CountDownLatch(1);

        // Mock the entire chain
        when(resourceServiceClient.getResourceData(resourceId)).thenReturn(mockResourceData);

        doAnswer(invocation -> {
            // Simulate the real service behavior
            String id = invocation.getArgument(0);
            resourceServiceClient.getResourceData(id);
            songServiceClient.saveSongMetadata(any(SongDTO.class));
            latch.countDown();
            return null;
        }).when(resourceProcessorService).process(anyString());

        // When
        sendMessageToKafka(resourceId);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        verify(resourceConsumer, times(1)).consume(resourceId);
        verify(resourceProcessorService, times(1)).process(resourceId);
    }

    private void sendMessageToKafka(String resourceId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, resourceId);
        testProducer.send(record);
        testProducer.flush();
    }
}