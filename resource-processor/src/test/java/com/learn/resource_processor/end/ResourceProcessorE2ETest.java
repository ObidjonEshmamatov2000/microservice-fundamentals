package com.learn.resource_processor.end;

import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceProcessorE2ETest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @MockitoBean
    private ResourceServiceClient resourceServiceClient;

    @MockitoBean
    private SongServiceClient songServiceClient;

    @Autowired
    private KafkaTemplate<String, Long> kafkaTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
        registry.add("management.endpoints.web.exposure.include", () -> "health,info");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.apache.kafka.common.serialization.LongSerializer");
        registry.add("spring.kafka.consumer.value-deserializer", () -> "org.apache.kafka.common.serialization.LongDeserializer");
    }

    @BeforeEach
    void resetMocks() {
        reset(resourceServiceClient, songServiceClient);
    }

    @Test
    @Order(1)
    @DisplayName("E2E: Should process complete flow with valid MP3 resource")
    void shouldProcessCompleteFlowWithValidMp3() {
        // Given
        Long resourceId = 12345L;
        byte[] mockMp3Data = createValidMp3Data();

        given(resourceServiceClient.getResourceData(resourceId))
                .willReturn(mockMp3Data);

        // When
        kafkaTemplate.send("resource-created", resourceId);

        // Then
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    verify(resourceServiceClient).getResourceData(resourceId);

                    ArgumentCaptor<SongDTO> captor = ArgumentCaptor.forClass(SongDTO.class);
                    verify(songServiceClient).saveSongMetadata(captor.capture());

                    SongDTO savedSong = captor.getValue();
                    assertThat(savedSong.getId()).isEqualTo(12345L);
                    assertThat(savedSong.getName()).isEqualTo("Unknown Title"); // Since we can't parse real MP3
                    assertThat(savedSong.getArtist()).isEqualTo("Unknown Artist");
                    assertThat(savedSong.getAlbum()).isEqualTo("Unknown Album");
                    assertThat(savedSong.getDuration()).matches("\\d{2}:\\d{2}");
                    assertThat(savedSong.getYear()).matches("\\d{4}");
                });
    }

    @Test
    @Order(2)
    @DisplayName("E2E: Application health check should be available")
    void applicationHealthShouldBeAvailable() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @Order(3)
    @DisplayName("E2E: Should handle burst of messages efficiently")
    void shouldHandleBurstOfMessagesEfficiently() {
        // Given
        int burstSize = 15;
        List<Long> resourceIds = IntStream.rangeClosed(2000, 2000 + burstSize - 1)
                .mapToObj(Long::valueOf)
                .toList();
        byte[] mockMp3Data = createValidMp3Data();

        resourceIds.forEach(id ->
                given(resourceServiceClient.getResourceData(id))
                        .willReturn(mockMp3Data));

        // When - Send all messages at once (burst)
        Instant startTime = Instant.now();
        CompletableFuture.runAsync(() -> {
            resourceIds.forEach(id -> kafkaTemplate.send("resource-created", id));
        });

        // Then
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    verify(songServiceClient, times(burstSize)).saveSongMetadata(any());
                });

        Duration processingTime = Duration.between(startTime, Instant.now());
        System.out.println("Processed burst of " + burstSize + " messages in " + processingTime.toMillis() + "ms");

        // Should handle burst efficiently (average less than 1 second per message)
        assertThat(processingTime.toSeconds()).isLessThan(burstSize);
    }

    private byte[] createValidMp3Data() {
        return "Mock MP3 data for testing purposes".getBytes(StandardCharsets.UTF_8);
    }
}
