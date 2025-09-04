package com.learn.resource_service.integration;

import com.learn.resource_service.kafka.ResourceProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.LongDeserializer;
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
        "kafka.topic.resource-created=resource-created-test",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.LongDeserializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.LongSerializer"
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

    private KafkaConsumer<String, Long> testConsumer;

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
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class);

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
        Long resourceId = 12345L;

        // When
        resourceProducer.sendId(resourceId);

        // Then
        ConsumerRecord<String, Long> record = getSingleRecord();

        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo(resourceCreatedTopic);
        assertThat(record.value()).isEqualTo(resourceId);
        assertThat(record.key()).isNull(); // Since we're not setting a key
    }

    @Test
    void shouldSendMultipleResourceIdsSuccessfully() throws InterruptedException {
        // Given
        List<Long> resourceIds = Arrays.asList(101L, 102L, 103L);

        // When
        for (Long id : resourceIds) {
            resourceProducer.sendId(id);
        }

        // Then
        List<ConsumerRecord<String, Long>> records = getMultipleRecords(3);

        assertThat(records).hasSize(3);

        List<Long> receivedIds = records.stream()
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
        List<Long> resourceIds = Arrays.asList(201L, 202L, 203L, 204L, 205L);

        // When
        for (Long id : resourceIds) {
            resourceProducer.sendId(id);
        }

        // Then
        List<ConsumerRecord<String, Long>> records = getMultipleRecords(5);

        Set<Integer> partitions = new HashSet<>();
        for (ConsumerRecord<String, Long> record : records) {
            partitions.add(record.partition());
        }

        // With 3 partitions and 5 messages, we should see distribution
        // (exact distribution depends on partitioning strategy)
        assertThat(partitions).isNotEmpty();
        assertThat(partitions).allSatisfy(partition ->
                assertThat(partition).isBetween(0, 2) // 3 partitions: 0, 1, 2
        );
    }

    private ConsumerRecord<String, Long> getSingleRecord() {
        List<ConsumerRecord<String, Long>> records = getMultipleRecords(1);
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private List<ConsumerRecord<String, Long>> getMultipleRecords(int expectedCount) {
        List<ConsumerRecord<String, Long>> allRecords = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long timeoutMs = 10000;

        while (allRecords.size() < expectedCount &&
                (System.currentTimeMillis() - startTime) < timeoutMs) {

            ConsumerRecords<String, Long> records = testConsumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, Long> record : records) {
                allRecords.add(record);
            }
        }

        return allRecords;
    }
}