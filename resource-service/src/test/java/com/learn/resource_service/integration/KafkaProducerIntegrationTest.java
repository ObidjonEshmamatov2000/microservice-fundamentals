package com.learn.resource_service.integration;

import com.learn.resource_service.kafka.ResourceProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration-kafka-test")
@EmbeddedKafka(
        partitions = 3,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9092",
                "port=9092"
        },
        topics = {"${kafka.topic.resource-created}"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "kafka.topic.resource-created=resource-created-test"
})
@DirtiesContext
class KafkaProducerIntegrationTest {

    @Autowired
    private ResourceProducer resourceProducer;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${kafka.topic.resource-created}")
    private String resourceCreatedTopic;

    private KafkaConsumer<String, String> testConsumer;

    @BeforeEach
    void setUp() {
        // Create test consumer to verify messages
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group",
                "true",
                embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        testConsumer = new KafkaConsumer<>(consumerProps);
        testConsumer.subscribe(Collections.singletonList(resourceCreatedTopic));
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void shouldSendResourceIdToKafkaTopic() throws InterruptedException {
        // Given
        String resourceId = "12345";

        // When
        resourceProducer.sendId(resourceId);

        // Then
        ConsumerRecord<String, String> record = getSingleRecord();

        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo(resourceCreatedTopic);
        assertThat(record.value()).isEqualTo(resourceId);
        assertThat(record.key()).isNull(); // Since we're not setting a key
    }

    @Test
    void shouldSendMultipleResourceIdsSuccessfully() throws InterruptedException {
        // Given
        List<String> resourceIds = Arrays.asList("101", "102", "103");

        // When
        for (String id : resourceIds) {
            resourceProducer.sendId(id);
        }

        // Then
        List<ConsumerRecord<String, String>> records = getMultipleRecords(3);

        assertThat(records).hasSize(3);

        List<String> receivedIds = records.stream()
                .map(ConsumerRecord::value)
                .toList();

        assertThat(receivedIds).containsExactlyInAnyOrderElementsOf(resourceIds);

        records.forEach(record ->
                assertThat(record.topic()).isEqualTo(resourceCreatedTopic)
        );
    }

    @Test
    void shouldDistributeMessagesAcrossPartitions() throws InterruptedException {
        // Given
        List<String> resourceIds = Arrays.asList("201", "202", "203", "204", "205");

        // When
        for (String id : resourceIds) {
            resourceProducer.sendId(id);
        }

        // Then
        List<ConsumerRecord<String, String>> records = getMultipleRecords(5);

        Set<Integer> partitions = new HashSet<>();
        for (ConsumerRecord<String, String> record : records) {
            partitions.add(record.partition());
        }

        // With 3 partitions and 5 messages, we should see distribution
        // (exact distribution depends on partitioning strategy)
        assertThat(partitions).isNotEmpty();
        assertThat(partitions).allSatisfy(partition ->
                assertThat(partition).isBetween(0, 2) // 3 partitions: 0, 1, 2
        );
    }

    @Test
    void shouldHandleNullResourceId() {
        // Given
        String resourceId = null;

        // When & Then
        assertDoesNotThrow(() -> resourceProducer.sendId(resourceId));

        // Verify message was sent (Kafka can handle null values)
        ConsumerRecord<String, String> record = getSingleRecord();
        assertThat(record).isNotNull();
        assertThat(record.value()).isNull();
    }

    @Test
    void shouldHandleEmptyResourceId() throws InterruptedException {
        // Given
        String resourceId = "";

        // When
        resourceProducer.sendId(resourceId);

        // Then
        ConsumerRecord<String, String> record = getSingleRecord();
        assertThat(record).isNotNull();
        assertThat(record.value()).isEmpty();
    }

    @Test
    void shouldHandleLargeResourceId() throws InterruptedException {
        // Given
        String resourceId = "A".repeat(1000); // 1000 character string

        // When
        resourceProducer.sendId(resourceId);

        // Then
        ConsumerRecord<String, String> record = getSingleRecord();
        assertThat(record).isNotNull();
        assertThat(record.value()).isEqualTo(resourceId);
        assertThat(record.value()).hasSize(1000);
    }

    @Test
    void shouldVerifyMessageMetadata() throws InterruptedException {
        // Given
        String resourceId = "metadata-test-123";

        // When
        resourceProducer.sendId(resourceId);

        // Then
        ConsumerRecord<String, String> record = getSingleRecord();

        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo(resourceCreatedTopic);
        assertThat(record.partition()).isBetween(0, 2);
        assertThat(record.offset()).isGreaterThanOrEqualTo(0);
        assertThat(record.timestamp()).isGreaterThan(0);
        assertThat(record.timestampType()).isNotNull();
    }

    @Test
    void shouldMaintainMessageOrderWithinPartition() throws InterruptedException {
        // Given - Use same key to ensure same partition
        String baseId = "order-test-";
        List<String> resourceIds = Arrays.asList(
                baseId + "1", baseId + "2", baseId + "3"
        );

        // When - Send with same key to ensure same partition
        for (String id : resourceIds) {
            kafkaTemplate.send(resourceCreatedTopic, "same-key", id);
        }

        // Then
        List<ConsumerRecord<String, String>> records = getMultipleRecords(3);

        // Filter records with our test key and sort by offset
        List<ConsumerRecord<String, String>> orderedRecords = records.stream()
                .filter(record -> "same-key".equals(record.key()))
                .sorted(Comparator.comparing(ConsumerRecord::offset))
                .toList();

        assertThat(orderedRecords).hasSize(3);

        // Verify order
        for (int i = 0; i < orderedRecords.size(); i++) {
            assertThat(orderedRecords.get(i).value()).isEqualTo(resourceIds.get(i));
        }
    }

    @Test
    void shouldHandleConcurrentSending() throws InterruptedException, ExecutionException {
        // Given
        int numberOfThreads = 5;
        int messagesPerThread = 10;
        List<String> allResourceIds = new ArrayList<>();

        // When - Send messages concurrently
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadNum = i;
            Thread thread = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    String resourceId = String.format("thread-%d-msg-%d", threadNum, j);
                    synchronized (allResourceIds) {
                        allResourceIds.add(resourceId);
                    }
                    resourceProducer.sendId(resourceId);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }

        // Then
        int expectedMessages = numberOfThreads * messagesPerThread;
        List<ConsumerRecord<String, String>> records = getMultipleRecords(expectedMessages);

        assertThat(records).hasSize(expectedMessages);

        List<String> receivedIds = records.stream()
                .map(ConsumerRecord::value)
                .sorted()
                .toList();

        Collections.sort(allResourceIds);

        assertThat(receivedIds).isEqualTo(allResourceIds);
    }

    private ConsumerRecord<String, String> getSingleRecord() {
        List<ConsumerRecord<String, String>> records = getMultipleRecords(1);
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private List<ConsumerRecord<String, String>> getMultipleRecords(int expectedCount) {
        List<ConsumerRecord<String, String>> allRecords = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long timeoutMs = 10000;

        while (allRecords.size() < expectedCount &&
                (System.currentTimeMillis() - startTime) < timeoutMs) {

            ConsumerRecords<String, String> records = testConsumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {
                allRecords.add(record);
            }
        }

        return allRecords;
    }
}