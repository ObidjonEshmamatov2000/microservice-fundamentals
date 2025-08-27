package com.learn.resource_service.controller;

import com.learn.resource_service.entity.Resource;
import com.learn.resource_service.service.ResourceService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/resources")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping(consumes = "audio/mpeg")
    public ResponseEntity<?> uploadResource(@RequestBody byte[] mp3Data) {
        Long resourceId = resourceService.uploadResource(mp3Data);
        return ResponseEntity.ok().body(Map.of("id", resourceId));
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<?> getResource(@PathVariable("id") Long id) {
        Resource resource = resourceService.getResourceById(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getResourceV2(@PathVariable Long id) {
        try {
            // Get the actual MP3 file content
            byte[] content = resourceService.getResourceContent(id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setContentLength(content.length);

            // Optional: Add Content-Disposition for download
            headers.setContentDisposition(
                    ContentDisposition.builder("attachment")
                            .filename("resource_" + id + ".mp3")
                            .build()
            );

            return new ResponseEntity<>(content, headers, HttpStatus.OK);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteResources(@RequestParam("id") String csvIds) {
        List<Long> ids = resourceService.deleteResourcesByIds(csvIds);
        return ResponseEntity.ok().body(Map.of("ids", ids));
    }
}
