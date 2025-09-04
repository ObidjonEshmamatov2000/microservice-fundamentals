package com.learn.songservice.e2e;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@Testcontainers
@Transactional
@DirtiesContext
public class SongServiceEndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("song_db_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SongRepository songRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        songRepository.deleteAll();
    }

    @Test
    void testCreateSongSuccess() throws Exception {
        SongDTO songDTO = createValidSongDTO(1L, "Bohemian Rhapsody", "Queen", "A Night at the Opera", "05:55", "1975");

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        // Verify song was saved in database
        mockMvc.perform(get("/songs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bohemian Rhapsody"))
                .andExpect(jsonPath("$.artist").value("Queen"))
                .andExpect(jsonPath("$.album").value("A Night at the Opera"));
    }

    @Test
    void testCreateSongConflict() throws Exception {
        SongDTO songDTO = createValidSongDTO(1L, "Test Song", "Test Artist", "Test Album", "03:30", "2023");

        // Create song first time
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isOk());

        // Try to create same song again
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(songDTO)))
                .andExpect(status().isConflict());
    }

    @Test
    void testCreateSongValidationFailure() throws Exception {
        SongDTO invalidSong = new SongDTO();
        invalidSong.setId(null); // Required field missing
        invalidSong.setName(""); // Blank
        invalidSong.setArtist("Test Artist");
        invalidSong.setAlbum("Test Album");
        invalidSong.setDuration("invalid"); // Invalid format
        invalidSong.setYear("1800"); // Invalid year

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetSongSuccess() throws Exception {
        // Create a song first
        Song song = createAndSaveSong(1L, "Hotel California", "Eagles", "Hotel California", "06:30", "1976");

        mockMvc.perform(get("/songs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Hotel California"))
                .andExpect(jsonPath("$.artist").value("Eagles"))
                .andExpect(jsonPath("$.album").value("Hotel California"))
                .andExpect(jsonPath("$.duration").value("06:30"))
                .andExpect(jsonPath("$.year").value("1976"));
    }

    @Test
    void testGetSongNotFound() throws Exception {
        mockMvc.perform(get("/songs/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetSongInvalidId() throws Exception {
        mockMvc.perform(get("/songs/0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/songs/-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteSongsSuccess() throws Exception {
        // Create multiple songs
        createAndSaveSong(1L, "Song 1", "Artist 1", "Album 1", "03:30", "2020");
        createAndSaveSong(2L, "Song 2", "Artist 2", "Album 2", "04:15", "2021");
        createAndSaveSong(3L, "Song 3", "Artist 3", "Album 3", "02:45", "2022");

        mockMvc.perform(delete("/songs")
                        .param("id", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(3)))
                .andExpect(jsonPath("$.ids", containsInAnyOrder(1, 2, 3)));

        // Verify songs were deleted
        mockMvc.perform(get("/songs/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/songs/2"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/songs/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteSongsPartialSuccess() throws Exception {
        // Create only some of the songs
        createAndSaveSong(1L, "Song 1", "Artist 1", "Album 1", "03:30", "2020");
        createAndSaveSong(3L, "Song 3", "Artist 3", "Album 3", "02:45", "2022");

        mockMvc.perform(delete("/songs")
                        .param("id", "1,2,3,4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(2)))
                .andExpect(jsonPath("$.ids", containsInAnyOrder(1, 3)));

        // Verify only existing songs were deleted
        mockMvc.perform(get("/songs/1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/songs/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteSongsInvalidCsvIds() throws Exception {
        mockMvc.perform(delete("/songs")
                        .param("id", ""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/songs")
                        .param("id", "1,invalid,3"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/songs")
                        .param("id", "0,1,2"))
                .andExpect(status().isBadRequest());

        // Test CSV string too long (>= 200 characters)
        String longCsvIds = "1,".repeat(100); // Creates a string longer than 200 chars
        mockMvc.perform(delete("/songs")
                        .param("id", longCsvIds))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCompleteWorkflow() throws Exception {
        // 1. Create multiple songs
        SongDTO song1 = createValidSongDTO(1L, "Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", "08:02", "1971");
        SongDTO song2 = createValidSongDTO(2L, "Imagine", "John Lennon", "Imagine", "03:01", "1971");
        SongDTO song3 = createValidSongDTO(3L, "Smells Like Teen Spirit", "Nirvana", "Nevermind", "05:01", "1991");

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(song1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(song2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(song3)))
                .andExpect(status().isOk());

        // 2. Retrieve all songs to verify they exist
        mockMvc.perform(get("/songs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stairway to Heaven"));

        mockMvc.perform(get("/songs/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Imagine"));

        mockMvc.perform(get("/songs/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Smells Like Teen Spirit"));

        // 3. Delete some songs
        mockMvc.perform(delete("/songs")
                        .param("id", "1,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids", hasSize(2)));

        // 4. Verify deletions
        mockMvc.perform(get("/songs/1"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/songs/2"))
                .andExpect(status().isOk()); // Should still exist

        mockMvc.perform(get("/songs/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testFieldValidationConstraints() throws Exception {
        // Test name too long
        SongDTO longNameSong = createValidSongDTO(1L, "a".repeat(101), "Artist", "Album", "03:30", "2023");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longNameSong)))
                .andExpect(status().isBadRequest());

        // Test invalid duration format
        SongDTO invalidDurationSong = createValidSongDTO(2L, "Song", "Artist", "Album", "3:30", "2023");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDurationSong)))
                .andExpect(status().isBadRequest());

        // Test invalid year
        SongDTO invalidYearSong = createValidSongDTO(3L, "Song", "Artist", "Album", "03:30", "2100");
        mockMvc.perform(post("/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidYearSong)))
                .andExpect(status().isBadRequest());
    }

    private SongDTO createValidSongDTO(Long id, String name, String artist, String album, String duration, String year) {
        SongDTO dto = new SongDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setArtist(artist);
        dto.setAlbum(album);
        dto.setDuration(duration);
        dto.setYear(year);
        return dto;
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