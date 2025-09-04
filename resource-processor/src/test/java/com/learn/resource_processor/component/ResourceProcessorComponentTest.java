package com.learn.resource_processor.component;

import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import com.learn.resource_processor.service.ResourceProcessorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "resource-service.url=localhost",
        "resource-service.port=8081",
        "song-service.url=localhost",
        "song-service.port=8082"
})
@DirtiesContext
class ResourceProcessorComponentTest {

    @Autowired
    private ResourceProcessorService resourceProcessorService;

    @MockitoBean
    private ResourceServiceClient resourceServiceClient;

    @MockitoBean
    private SongServiceClient songServiceClient;

    private byte[] createMockMp3Data() {
        // This is a simplified representation for testing purposes
        String mockData = "ID3\u0003\u0000\u0000\u0000\u0000\u0000\u0000" +
                "TIT2\u0000\u0000\u0000\u000bTest Song\u0000" +
                "TPE1\u0000\u0000\u0000\u000bTest Artist\u0000" +
                "TALB\u0000\u0000\u0000\u000bTest Album\u0000";
        return mockData.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Should successfully process resource with valid MP3 data")
    void shouldProcessResourceWithValidMp3Data() {
        // Given
        Long resourceId = 123L;
        byte[] mockMp3Data = createMockMp3Data();

        given(resourceServiceClient.getResourceData(resourceId))
                .willReturn(mockMp3Data);

        // When
        resourceProcessorService.process(resourceId);

        // Then
        verify(resourceServiceClient).getResourceData(resourceId);

        ArgumentCaptor<SongDTO> songCaptor = ArgumentCaptor.forClass(SongDTO.class);
        verify(songServiceClient).saveSongMetadata(songCaptor.capture());

        SongDTO savedSong = songCaptor.getValue();
        assertThat(savedSong.getId()).isEqualTo(123L);
        assertThat(savedSong.getName()).isNotNull();
        assertThat(savedSong.getArtist()).isNotNull();
        assertThat(savedSong.getAlbum()).isNotNull();
        assertThat(savedSong.getDuration()).matches("\\d{2}:\\d{2}");
        assertThat(savedSong.getYear()).matches("\\d{4}");
    }

    @Test
    @DisplayName("Should handle resource service failure gracefully")
    void shouldHandleResourceServiceFailure() {
        // Given
        Long resourceId = 456L;
        given(resourceServiceClient.getResourceData(resourceId))
                .willThrow(new RestClientException("Resource service unavailable"));

        // When & Then
        assertThatThrownBy(() -> resourceProcessorService.process(resourceId))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Resource service unavailable");

        verify(resourceServiceClient).getResourceData(resourceId);
        verify(songServiceClient, never()).saveSongMetadata(any());
    }

    @Test
    @DisplayName("Should handle song service failure gracefully")
    void shouldHandleSongServiceFailure() {
        // Given
        Long resourceId = 789L;
        byte[] mockMp3Data = createMockMp3Data();

        given(resourceServiceClient.getResourceData(resourceId))
                .willReturn(mockMp3Data);
        doThrow(new RestClientException("Song service unavailable"))
                .when(songServiceClient).saveSongMetadata(any());

        // When & Then
        assertThatThrownBy(() -> resourceProcessorService.process(resourceId))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Song service unavailable");

        verify(resourceServiceClient).getResourceData(resourceId);
        verify(songServiceClient).saveSongMetadata(any());
    }

    @Test
    @DisplayName("Should process resource with missing metadata using defaults")
    void shouldProcessResourceWithMissingMetadata() {
        // Given
        Long resourceId = 999L;
        byte[] emptyMp3Data = new byte[0]; // Empty data to trigger defaults

        given(resourceServiceClient.getResourceData(resourceId))
                .willReturn(emptyMp3Data);

        // When
        resourceProcessorService.process(resourceId);

        // Then
        ArgumentCaptor<SongDTO> songCaptor = ArgumentCaptor.forClass(SongDTO.class);
        verify(songServiceClient).saveSongMetadata(songCaptor.capture());

        SongDTO savedSong = songCaptor.getValue();
        assertThat(savedSong.getId()).isEqualTo(999L);
        assertThat(savedSong.getName()).isEqualTo("Unknown Title");
        assertThat(savedSong.getArtist()).isEqualTo("Unknown Artist");
        assertThat(savedSong.getAlbum()).isEqualTo("Unknown Album");
        assertThat(savedSong.getDuration()).isEqualTo("00:00");
        assertThat(savedSong.getYear()).isEqualTo("1900");
    }
}

