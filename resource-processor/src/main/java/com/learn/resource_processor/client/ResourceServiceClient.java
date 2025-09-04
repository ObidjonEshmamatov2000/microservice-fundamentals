package com.learn.resource_processor.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ResourceServiceClient {
    private final RestTemplate restTemplate;
    private final String resourceServiceUrl;

    public ResourceServiceClient(RestTemplate restTemplate,
                                 @Value("${resource-service.url}") String url,
                                 @Value("${resource-service.port}") String port) {
        this.restTemplate = restTemplate;
        this.resourceServiceUrl = "http://" + url + ":" + port + "/resources";
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public byte[] getResourceData(Long resourceId) {
        String url = resourceServiceUrl + "/" + resourceId;
        return restTemplate.getForObject(url, byte[].class);
    }
}
