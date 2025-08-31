package com.learn.resource_processor.service.impl;

import com.learn.resource_processor.client.ResourceServiceClient;
import com.learn.resource_processor.client.SongServiceClient;
import com.learn.resource_processor.dto.SongDTO;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceProcessorServiceImplTest {

    @Mock
    private ResourceServiceClient resourceServiceClient;

    @Mock
    private SongServiceClient songServiceClient;

    @InjectMocks
    private ResourceProcessorServiceImpl resourceProcessorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void process_withValidResourceId_callsClients() throws IOException, TikaException, SAXException {
        // Arrange
        String resourceId = "1";
        byte[] mockData = new byte[]{1, 2, 3};
        when(resourceServiceClient.getResourceData(resourceId)).thenReturn(mockData);

        // Act
        resourceProcessorService.process(resourceId);

        // Assert
        verify(resourceServiceClient).getResourceData(resourceId);
        ArgumentCaptor<SongDTO> songCaptor = ArgumentCaptor.forClass(SongDTO.class);
        verify(songServiceClient).saveSongMetadata(songCaptor.capture());

        SongDTO capturedSong = songCaptor.getValue();
        assertNotNull(capturedSong);
        assertEquals(1L, capturedSong.getId());
        assertNotNull(capturedSong.getName());
        assertNotNull(capturedSong.getArtist());
        assertNotNull(capturedSong.getAlbum());
    }
}