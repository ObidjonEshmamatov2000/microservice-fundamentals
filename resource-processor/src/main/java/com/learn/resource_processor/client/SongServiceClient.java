package com.learn.resource_processor.client;

import com.learn.resource_processor.dto.SongDTO;
import org.springframework.beans.factory.annotation.Value;
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

    public void saveSongMetadata(SongDTO songDTO) {
        restTemplate.postForObject(songServiceUrl, songDTO, SongDTO.class);
    }
}
