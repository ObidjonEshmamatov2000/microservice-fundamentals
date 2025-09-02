package com.learn.songservice.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.songservice.controller.SongController;
import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SongController.class)
class SongControllerComponentTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongService songService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createSong_ValidInput_ShouldReturnOkWithId() throws Exception {
        // Given
        SongDTO songDTO = createValidSongDTO(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");
        Song createdSong = createSong(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");

        when(songService.createSong(any(SongDTO.class))).thenReturn(createdSong);

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void createSong_InvalidInput_ShouldReturnBadRequest() throws Exception {
        // Given - invalid DTO with missing required fields
        SongDTO invalidDTO = new SongDTO();
        invalidDTO.setId(1L);
        // Missing other required fields

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSong_ValidId_ShouldReturnSong() throws Exception {
        // Given
        Song song = createSong(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");
        when(songService.getSong(1L)).thenReturn(song);

        // When & Then
        mockMvc.perform(get("/songs/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test Song"))
                .andExpect(jsonPath("$.artist").value("Test Artist"))
                .andExpect(jsonPath("$.album").value("Test Album"))
                .andExpect(jsonPath("$.duration").value("03:45"))
                .andExpect(jsonPath("$.year").value("2023"));
    }

    @Test
    void getSong_NonExistentId_ShouldReturnNotFound() throws Exception {
        // Given
        when(songService.getSong(999L)).thenThrow(new NoSuchElementException("Song with ID=999 not found"));

        // When & Then
        mockMvc.perform(get("/songs/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSong_InvalidId_ShouldReturnBadRequest() throws Exception {
        // Given
        when(songService.getSong(0L)).thenThrow(new IllegalArgumentException("Invalid ID"));

        // When & Then
        mockMvc.perform(get("/songs/{id}", 0L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSongs_ValidIds_ShouldReturnDeletedIds() throws Exception {
        // Given
        List<Long> deletedIds = Arrays.asList(1L, 2L);
        when(songService.deleteSongs("1,2")).thenReturn(deletedIds);

        // When & Then
        mockMvc.perform(delete("/songs")
                        .param("id", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids").isArray())
                .andExpect(jsonPath("$.ids[0]").value(1L))
                .andExpect(jsonPath("$.ids[1]").value(2L));
    }

    @Test
    void deleteSongs_InvalidIds_ShouldReturnBadRequest() throws Exception {
        // Given
        when(songService.deleteSongs("")).thenThrow(new IllegalArgumentException("CSV IDs are required"));

        // When & Then
        mockMvc.perform(delete("/songs")
                        .param("id", ""))
                .andExpect(status().isBadRequest());
    }

    private SongDTO createValidSongDTO(Long id, String name, String artist, String album, String duration, String year) {
        SongDTO songDTO = new SongDTO();
        songDTO.setId(id);
        songDTO.setName(name);
        songDTO.setArtist(artist);
        songDTO.setAlbum(album);
        songDTO.setDuration(duration);
        songDTO.setYear(year);
        return songDTO;
    }

    private Song createSong(Long id, String name, String artist, String album, String duration, String year) {
        Song song = new Song();
        song.setId(id);
        song.setName(name);
        song.setArtist(artist);
        song.setAlbum(album);
        song.setDuration(duration);
        song.setYear(year);
        return song;
    }
}
