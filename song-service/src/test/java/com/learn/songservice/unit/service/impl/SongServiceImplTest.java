package com.learn.songservice.unit.service.impl;

import com.learn.songservice.dto.SongDTO;
import com.learn.songservice.entity.Song;
import com.learn.songservice.exception.ConflictException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SongServiceImplTest {

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private SongServiceImpl songService;

    private SongDTO songDTO;
    private Song song;

    @BeforeEach
    void setUp() {
        songDTO = new SongDTO();
        songDTO.setId(1L);
        songDTO.setName("Test Song");
        songDTO.setArtist("Test Artist");
        songDTO.setAlbum("Test Album");
        songDTO.setDuration("180");
        songDTO.setYear("2023");

        song = new Song();
        song.setId(1L);
        song.setName("Test Song");
        song.setArtist("Test Artist");
        song.setAlbum("Test Album");
        song.setDuration("180");
        song.setYear("2023");
    }

    @Test
    void createSong_success() {
        // Arrange
        when(songRepository.existsById(1L)).thenReturn(false);
        when(songRepository.save(any(Song.class))).thenReturn(song);

        // Act
        Song result = songService.createSong(songDTO);

        // Assert
        assertNotNull(result);
        assertEquals(song.getId(), result.getId());
        assertEquals(song.getName(), result.getName());
        verify(songRepository).existsById(1L);
        verify(songRepository).save(any(Song.class));
    }

    @Test
    void createSong_throwsConflictException_whenSongExists() {
        // Arrange
        when(songRepository.existsById(1L)).thenReturn(true);

        // Act & Assert
        assertThrows(ConflictException.class, () -> songService.createSong(songDTO));
        verify(songRepository).existsById(1L);
        verify(songRepository, never()).save(any(Song.class));
    }

    @Test
    void getSong_success() {
        // Arrange
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        // Act
        Song result = songService.getSong(1L);

        // Assert
        assertNotNull(result);
        assertEquals(song.getId(), result.getId());
        assertEquals(song.getName(), result.getName());
        verify(songRepository).findById(1L);
    }

    @Test
    void getSong_throwsNoSuchElementException_whenSongNotFound() {
        // Arrange
        when(songRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NoSuchElementException.class, () -> songService.getSong(1L));
        verify(songRepository).findById(1L);
    }

    @Test
    void getSong_throwsIllegalArgumentException_whenInvalidId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> songService.getSong(0L));
        assertThrows(IllegalArgumentException.class, () -> songService.getSong(null));
        verify(songRepository, never()).findById(any());
    }

    @Test
    void deleteSongs_success() {
        // Arrange
        when(songRepository.existsById(1L)).thenReturn(true);
        when(songRepository.existsById(2L)).thenReturn(true);
        doNothing().when(songRepository).deleteById(any());

        // Act
        List<Long> result = songService.deleteSongs("1,2");

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.contains(1L));
        assertTrue(result.contains(2L));
        verify(songRepository).existsById(1L);
        verify(songRepository).existsById(2L);
        verify(songRepository).deleteById(1L);
        verify(songRepository).deleteById(2L);
    }

    @Test
    void deleteSongs_skipsNonExistentSongs() {
        // Arrange
        when(songRepository.existsById(1L)).thenReturn(true);
        when(songRepository.existsById(2L)).thenReturn(false);
        doNothing().when(songRepository).deleteById(any());

        // Act
        List<Long> result = songService.deleteSongs("1,2");

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.contains(1L));
        assertFalse(result.contains(2L));
        verify(songRepository).existsById(1L);
        verify(songRepository).existsById(2L);
        verify(songRepository).deleteById(1L);
        verify(songRepository, never()).deleteById(2L);
    }

    @Test
    void deleteSongs_throwsIllegalArgumentException_whenCsvIsEmpty() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> songService.deleteSongs(""));
        assertThrows(IllegalArgumentException.class, () -> songService.deleteSongs(null));
        verifyNoInteractions(songRepository);
    }

    @Test
    void validateId_throwsIllegalArgumentException_whenInvalidId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> songService.validateId(null));
        assertThrows(IllegalArgumentException.class, () -> songService.validateId(0L));
        assertThrows(IllegalArgumentException.class, () -> songService.validateId(-1L));
    }

    @Test
    void validateCsvIds_success() {
        // Act & Assert - if no exception is thrown, the test passes
        assertDoesNotThrow(() -> songService.validateCsvIds("1,2,3"));
    }

    @Test
    void validateCsvIds_throwsIllegalArgumentException_whenInvalidFormat() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds("1,abc,3"));
    }

    @Test
    void validateCsvIds_throwsIllegalArgumentException_whenEmpty() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds(""));
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds(null));
    }

    @Test
    void validateCsvIds_throwsIllegalArgumentException_whenTooLong() {
        // Arrange
        StringBuilder longCsv = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longCsv.append(i).append(",");
        }

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> songService.validateCsvIds(longCsv.toString()));
    }
}
