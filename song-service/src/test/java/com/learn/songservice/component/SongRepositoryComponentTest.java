package com.learn.songservice.component;

import com.learn.songservice.entity.Song;
import com.learn.songservice.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class SongRepositoryComponentTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SongRepository songRepository;

    private Song testSong;

    @BeforeEach
    void setUp() {
        testSong = createValidSong(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");
    }

    @Test
    void save_ValidSong_ShouldPersistSong() {
        // When
        Song savedSong = songRepository.save(testSong);
        entityManager.flush();

        // Then
        assertNotNull(savedSong);
        assertEquals(testSong.getId(), savedSong.getId());
        assertEquals(testSong.getName(), savedSong.getName());

        // Verify it's actually persisted
        Song foundSong = entityManager.find(Song.class, 1L);
        assertNotNull(foundSong);
        assertEquals("Test Song", foundSong.getName());
    }

    @Test
    void findById_ExistingSong_ShouldReturnSong() {
        // Given
        entityManager.persistAndFlush(testSong);

        // When
        Optional<Song> result = songRepository.findById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Test Song", result.get().getName());
        assertEquals("Test Artist", result.get().getArtist());
    }

    @Test
    void findById_NonExistentId_ShouldReturnEmpty() {
        // When
        Optional<Song> result = songRepository.findById(999L);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void existsById_ExistingSong_ShouldReturnTrue() {
        // Given
        entityManager.persistAndFlush(testSong);

        // When
        boolean exists = songRepository.existsById(1L);

        // Then
        assertTrue(exists);
    }

    @Test
    void existsById_NonExistentId_ShouldReturnFalse() {
        // When
        boolean exists = songRepository.existsById(999L);

        // Then
        assertFalse(exists);
    }

    @Test
    void deleteById_ExistingSong_ShouldRemoveSong() {
        // Given
        entityManager.persistAndFlush(testSong);
        assertTrue(songRepository.existsById(1L));

        // When
        songRepository.deleteById(1L);
        entityManager.flush();

        // Then
        assertFalse(songRepository.existsById(1L));
        Optional<Song> result = songRepository.findById(1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_MultipleSongs_ShouldReturnAllSongs() {
        // Given
        Song song1 = createValidSong(1L, "Song 1", "Artist 1", "Album 1", "03:30", "2021");
        Song song2 = createValidSong(2L, "Song 2", "Artist 2", "Album 2", "04:00", "2022");
        Song song3 = createValidSong(3L, "Song 3", "Artist 3", "Album 3", "02:15", "2023");

        entityManager.persist(song1);
        entityManager.persist(song2);
        entityManager.persist(song3);
        entityManager.flush();

        // When
        List<Song> result = songRepository.findAll();

        // Then
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getName().equals("Song 1")));
        assertTrue(result.stream().anyMatch(s -> s.getName().equals("Song 2")));
        assertTrue(result.stream().anyMatch(s -> s.getName().equals("Song 3")));
    }

    @Test
    void save_UpdateExistingSong_ShouldUpdateSong() {
        // Given
        entityManager.persistAndFlush(testSong);

        // When
        testSong.setName("Updated Song Name");
        Song updatedSong = songRepository.save(testSong);
        entityManager.flush();

        // Then
        assertEquals("Updated Song Name", updatedSong.getName());

        // Verify persistence
        Song foundSong = entityManager.find(Song.class, 1L);
        assertEquals("Updated Song Name", foundSong.getName());
    }

    @Test
    void testDatabaseConstraints_ShouldEnforceNotNull() {
        // Given
        Song invalidSong = new Song();
        invalidSong.setId(1L);
        // Missing required fields

        // When & Then
        assertThrows(Exception.class, () -> {
            songRepository.save(invalidSong);
            entityManager.flush();
        });
    }

    private Song createValidSong(Long id, String name, String artist, String album, String duration, String year) {
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
