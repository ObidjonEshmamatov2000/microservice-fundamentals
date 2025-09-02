package com.learn.songservice.component;

import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.repository.SongRepository;
import com.learn.songservice.service.impl.SongServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SongServiceComponentTest {

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private SongServiceImpl songService;

    private SongDTO validSongDTO;
    private Song validSong;

    @BeforeEach
    void setUp() {
        validSongDTO = createValidSongDTO(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");
        validSong = createValidSong(1L, "Test Song", "Test Artist", "Test Album", "03:45", "2023");
    }

    @Test
    void createSong_ValidInput_ShouldReturnCreatedSong() {
        // Given
        when(songRepository.existsById(1L)).thenReturn(false);
        when(songRepository.save(any(Song.class))).thenReturn(validSong);

        // When
        Song result = songService.createSong(validSongDTO);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Song", result.getName());
        verify(songRepository).existsById(1L);
        verify(songRepository).save(any(Song.class));
    }

    @Test
    void getSong_ValidId_ShouldReturnSong() {
        // Given
        when(songRepository.findById(1L)).thenReturn(Optional.of(validSong));

        // When
        Song result = songService.getSong(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Song", result.getName());
        verify(songRepository).findById(1L);
    }

    @Test
    void getSong_NonExistentId_ShouldThrowException() {
        // Given
        when(songRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        NoSuchElementException exception = assertThrows(NoSuchElementException.class,
                () -> songService.getSong(999L));

        assertEquals("Song with ID=999 not found", exception.getMessage());
        verify(songRepository).findById(999L);
    }

    @Test
    void getSong_InvalidId_ShouldThrowIllegalArgumentException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> songService.getSong(0L));

        assertEquals("Invalid ID", exception.getMessage());
        verify(songRepository, never()).findById(any());
    }

    @Test
    void getSong_NullId_ShouldThrowIllegalArgumentException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> songService.getSong(null));

        assertEquals("Invalid ID", exception.getMessage());
        verify(songRepository, never()).findById(any());
    }

    @Test
    void deleteSongs_ValidIds_ShouldReturnDeletedIds() {
        // Given
        when(songRepository.existsById(1L)).thenReturn(true);
        when(songRepository.existsById(2L)).thenReturn(true);
        when(songRepository.existsById(3L)).thenReturn(false);

        // When
        List<Long> result = songService.deleteSongs("1,2,3");

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(1L));
        assertTrue(result.contains(2L));
        assertFalse(result.contains(3L));

        verify(songRepository).existsById(1L);
        verify(songRepository).existsById(2L);
        verify(songRepository).existsById(3L);
        verify(songRepository).deleteById(1L);
        verify(songRepository).deleteById(2L);
        verify(songRepository, never()).deleteById(3L);
    }

    @Test
    void deleteSongs_EmptyIds_ShouldThrowException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> songService.deleteSongs(""));

        assertEquals("CSV IDs are required", exception.getMessage());
        verify(songRepository, never()).existsById(any());
    }

    @Test
    void deleteSongs_NullIds_ShouldThrowException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> songService.deleteSongs(null));

        assertEquals("CSV IDs are required", exception.getMessage());
        verify(songRepository, never()).existsById(any());
    }

    @Test
    void deleteSongs_TooLongCsv_ShouldThrowException() {
        // Given
        String longCsv = "1,".repeat(100); // Over 200 characters

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> songService.deleteSongs(longCsv));

        assertTrue(exception.getMessage().contains("CSV string length must be less than 200 characters"));
        verify(songRepository, never()).existsById(any());
    }

    @Test
    void validateId_ValidId_ShouldNotThrowException() {
        // When & Then
        assertDoesNotThrow(() -> songService.validateId(1L));
        assertDoesNotThrow(() -> songService.validateId(Long.MAX_VALUE));
    }

    @Test
    void validateId_InvalidIds_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> songService.validateId(null));
        assertThrows(IllegalArgumentException.class, () -> songService.validateId(0L));
        assertThrows(IllegalArgumentException.class, () -> songService.validateId(-1L));
    }

    @Test
    void validateCsvIds_ValidCsv_ShouldNotThrowException() {
        // When & Then
        assertDoesNotThrow(() -> songService.validateCsvIds("1"));
        assertDoesNotThrow(() -> songService.validateCsvIds("1,2,3"));
        assertDoesNotThrow(() -> songService.validateCsvIds("123,456,789"));
    }

    @Test
    void validateCsvIds_InvalidCsv_ShouldThrowException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds(null));
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds(""));
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds("0,1,2"));
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds("1,-1,2"));
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds("1,abc,2"));
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