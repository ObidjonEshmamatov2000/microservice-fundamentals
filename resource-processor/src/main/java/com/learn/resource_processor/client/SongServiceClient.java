package com.learn.resource_processor.client;

import com.learn.resource_processor.dto.SongDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SongServiceClient {
    private final RestTemplate restTemplate;
    private final String songServiceUrl;

    public SongServiceClient(RestTemplate restTemplate,
                             @Value("${song-service.url}") String url,
                             @Value("${song-service.port}") String port) {
        this.restTemplate = restTemplate;
        this.songServiceUrl = "http://" + url + ":" + port + "/songs";
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void saveSongMetadata(SongDTO songDTO) {
        restTemplate.postForObject(songServiceUrl, songDTO, SongDTO.class);
    }
}
