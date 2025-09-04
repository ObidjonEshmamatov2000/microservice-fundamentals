package com.learn.songservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SongServiceIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        songRepository.deleteAll();
    }

    @Test
    void createSong_ValidInput_ShouldReturnCreatedSong() throws Exception {
        // Given
        SongDTO songDTO = createValidSongDTO(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));

        // Verify song was saved to database
        var savedSong = songRepository.findById(1L);
        assert savedSong.isPresent();
        assert savedSong.get().getName().equals("Test Song");
    }

    @Test
    void createSong_DuplicateId_ShouldReturnConflict() throws Exception {
        // Given - create a song first
        Song existingSong = new Song();
        existingSong.setId(1L);
        existingSong.setName("Existing Song");
        existingSong.setArtist("Existing Artist");
        existingSong.setAlbum("Existing Album");
        existingSong.setDuration("03:30");
        existingSong.setYear("2022");
        songRepository.save(existingSong);

        SongDTO duplicateSongDTO = createValidSongDTO(1L, "New Song", "New Artist", "New Album", "04:00", "2023");

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateSongDTO)))
                .andExpect(status().isConflict());
    }

    @Test
    void createSong_InvalidInput_ShouldReturnBadRequest() throws Exception {
        // Given - invalid song with missing required fields
        SongDTO invalidSongDTO = new SongDTO();
        invalidSongDTO.setId(1L);
        // Missing other required fields

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSongDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSong_InvalidDurationFormat_ShouldReturnBadRequest() throws Exception {
        // Given
        SongDTO songDTO = createValidSongDTO(1L, "Test Song", "Test Artist", "Test Album", "3:45", "2023"); // Invalid duration format

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createSong_InvalidYear_ShouldReturnBadRequest() throws Exception {
        // Given
        SongDTO songDTO = createValidSongDTO(1L, "Test Song", "Test Artist", "Test Album", "03:45", "1899"); // Invalid year

        // When & Then
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSong_ExistingId_ShouldReturnSong() throws Exception {
        // Given
        Song song = createAndSaveSong(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");

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
        // When & Then
        mockMvc.perform(get("/songs/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSong_InvalidId_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/songs/{id}", 0L))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/songs/{id}", -1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSongs_ValidIds_ShouldDeleteAndReturnDeletedIds() throws Exception {
        // Given
        createAndSaveSong(1L, "Song 1", "Artist 1", "Album 1", "03:30", "2021");
        createAndSaveSong(2L, "Song 2", "Artist 2", "Album 2", "04:00", "2022");
        createAndSaveSong(3L, "Song 3", "Artist 3", "Album 3", "02:45", "2023");

        // When & Then
        mockMvc.perform(delete("/songs")
                        .param("id", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(2)))
                .andExpect(jsonPath("$.ids", containsInAnyOrder(1, 2)));

        // Verify songs were deleted from database
        assert songRepository.findById(1L).isEmpty();
        assert songRepository.findById(2L).isEmpty();
        assert songRepository.findById(3L).isPresent(); // Should still exist
    }

    @Test
    void testDatabaseConstraints_ShouldEnforceValidation() throws Exception {
        // Test name too long
        SongDTO longNameSong = createValidSongDTO(1L, "a".repeat(101), "Artist", "Album", "03:30", "2023");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longNameSong)))
                .andExpect(status().isBadRequest());

        // Test artist too long
        SongDTO longArtistSong = createValidSongDTO(2L, "Song", "a".repeat(101), "Album", "03:30", "2023");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longArtistSong)))
                .andExpect(status().isBadRequest());

        // Test album too long
        SongDTO longAlbumSong = createValidSongDTO(3L, "Song", "Artist", "a".repeat(101), "03:30", "2023");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longAlbumSong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testConcurrentOperations_ShouldMaintainConsistency() throws Exception {
        // Given - create initial songs
        createAndSaveSong(1L, "Song 1", "Artist 1", "Album 1", "03:30", "2021");
        createAndSaveSong(2L, "Song 2", "Artist 2", "Album 2", "04:00", "2022");

        // When - perform concurrent-like operations
        // Create new song while others exist
        SongDTO newSong = createValidSongDTO(3L, "Song 3", "Artist 3", "Album 3", "02:30", "2023");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newSong)))
                .andExpect(status().isOk());

        // Delete some while creating was happening
        mockMvc.perform(delete("/songs")
                        .param("id", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(2)));

        // Then - verify final state
        mockMvc.perform(get("/songs/{id}", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Song 3"));

        mockMvc.perform(get("/songs/{id}", 1L))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSongs_LargeBatchWithinLimit_ShouldWork() throws Exception {
        // Given - create songs with IDs 1-20
        for (long i = 1; i <= 20; i++) {
            createAndSaveSong(i, "Song " + i, "Artist " + i, "Album " + i, "03:30", "2020");
        }

        // When - delete batch that's within 200 character limit
        String csvIds = "1,2,3,4,5,6,7,8,9,10"; // Well under 200 chars
        mockMvc.perform(delete("/songs")
                        .param("id", csvIds))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(10)));

        // Then - verify deletions
        for (long i = 1; i <= 10; i++) {
            mockMvc.perform(get("/songs/{id}", i))
                    .andExpect(status().isNotFound());
        }

        // Verify remaining songs still exist
        for (long i = 11; i <= 20; i++) {
            mockMvc.perform(get("/songs/{id}", i))
                    .andExpect(status().isOk());
        }
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

    private Song createAndSaveSong(Long id, String name, String artist, String album, String duration, String year) {
        Song song = new Song();
        song.setId(id);
        song.setName(name);
        song.setArtist(artist);
        song.setAlbum(album);
        song.setDuration(duration);
        song.setYear(year);
        return songRepository.save(song);
    }
}