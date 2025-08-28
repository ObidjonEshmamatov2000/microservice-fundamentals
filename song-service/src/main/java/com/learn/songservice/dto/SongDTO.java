package com.learn.songservice.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SongDTO {

    @NotNull(message = "ID is required")
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Artist is required")
    @Size(max = 100, message = "Artist must not exceed 100 characters")
    private String artist;

    @NotBlank(message = "Album is required")
    @Size(max = 100, message = "Album must not exceed 100 characters")
    private String album;

    @NotBlank(message = "Duration is required")
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Duration must be in mm:ss format with leading zeros")
    private String duration;

    @NotBlank(message = "Year is required")
    @Pattern(regexp = "^(19\\d{2}|20\\d{2}|2099)$", message = "Year must be between 1900 and 2099")
    private String year;
}
