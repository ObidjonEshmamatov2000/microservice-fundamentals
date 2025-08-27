package com.learn.resource_processor.service;

import com.learn.resource_processor.dto.SongDTO;

public interface ResourceProcessorService {
    SongDTO processMp3Resource(byte[] mp3Data);
}
