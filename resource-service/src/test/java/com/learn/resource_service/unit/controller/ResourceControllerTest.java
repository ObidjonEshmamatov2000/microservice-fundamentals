package com.learn.resource_service.unit.controller;

import com.learn.resource_service.controller.ResourceController;
import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceControllerTest {

    @Mock
    private ResourceService resourceService;

    @InjectMocks
    private ResourceController resourceController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void uploadResource_success() {
        byte[] mp3Data = new byte[]{1, 2, 3};
        when(resourceService.uploadResource(mp3Data)).thenReturn(42L);

        ResponseEntity<?> response = resourceController.uploadResource(mp3Data);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("id", 42L), response.getBody());
        verify(resourceService).uploadResource(mp3Data);
    }

    @Test
    void getResource_success() {
        Resource resource = new Resource();
        resource.setId(1L);
        when(resourceService.getResourceById(1L)).thenReturn(resource);

        ResponseEntity<?> response = resourceController.getResource(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals(resource, response.getBody());
        verify(resourceService).getResourceById(1L);
    }

    @Test
    void getResourceV2_success() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        when(resourceService.getResourceContent(1L)).thenReturn(content);

        ResponseEntity<byte[]> response = resourceController.getResourceV2(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(content, response.getBody());
        assertEquals(MediaType.parseMediaType("audio/mpeg"), response.getHeaders().getContentType());
        assertEquals(content.length, response.getHeaders().getContentLength());
        assertTrue(Objects.requireNonNull(response.getHeaders().getContentDisposition().getFilename()).contains("resource_1.mp3"));
        verify(resourceService).getResourceContent(1L);
    }

    @Test
    void getResourceV2_notFound() {
        when(resourceService.getResourceContent(1L)).thenThrow(new NoSuchElementException());

        ResponseEntity<byte[]> response = resourceController.getResourceV2(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getResourceV2_badRequest() {
        when(resourceService.getResourceContent(1L)).thenThrow(new IllegalArgumentException());

        ResponseEntity<byte[]> response = resourceController.getResourceV2(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getResourceV2_internalError() {
        when(resourceService.getResourceContent(1L)).thenThrow(new RuntimeException());

        ResponseEntity<byte[]> response = resourceController.getResourceV2(1L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void deleteResources_success() {
        List<Long> ids = List.of(1L, 2L);
        when(resourceService.deleteResourcesByIds("1,2")).thenReturn(ids);

        ResponseEntity<?> response = resourceController.deleteResources("1,2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("ids", ids), response.getBody());
        verify(resourceService).deleteResourcesByIds("1,2");
    }
}