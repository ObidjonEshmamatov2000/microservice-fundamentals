package com.learn.songservice.service;

import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;

import java.util.List;

public interface SongService {
    Song createSong(SongDTO songDTO);

    Song getSong(Long id);

    List<Long> deleteSongs(String csvIds);
}
