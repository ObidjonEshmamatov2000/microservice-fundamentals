package com.learn.songservice.unit.controller;

import com.learn.songservice.controller.SongController;
import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.service.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SongControllerTest {

    @Mock
    private SongService songService;

    @InjectMocks
    private SongController songController;

    private Song song;
    private SongDTO songDTO;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        song = new Song();
        song.setId(1L);
        song.setName("Test Song");

        songDTO = new SongDTO();
        songDTO.setId(1L);
        songDTO.setName("Test Song");
    }

    @Test
    void createSong_success() {
        when(songService.createSong(songDTO)).thenReturn(song);

        ResponseEntity<?> response = songController.createSong(songDTO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songService).createSong(songDTO);
    }

    @Test
    void getSong_success() {
        when(songService.getSong(1L)).thenReturn(song);

        ResponseEntity<?> response = songController.getSong(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(song, response.getBody());
        verify(songService).getSong(1L);
    }

    @Test
    void deleteSongs_success() {
        List<Long> ids = List.of(1L, 2L);
        when(songService.deleteSongs("1,2")).thenReturn(ids);

        ResponseEntity<?> response = songController.deleteSongs("1,2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("ids", ids), response.getBody());
        verify(songService).deleteSongs("1,2");
    }
}