package com.cinebook.movie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MovieRequest(
        @NotBlank(message = "Movie title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Synopsis / description is required")
        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,

        @NotBlank(message = "Poster URL is required")
        @Size(max = 500, message = "Poster URL must not exceed 500 characters")
        String posterUrl,

        @Size(max = 500, message = "Backdrop URL must not exceed 500 characters")
        String backdropUrl,

        @NotNull(message = "Genre ID is required")
        Long genreId,

        @NotBlank(message = "Language is required")
        @Size(max = 60, message = "Language must not exceed 60 characters")
        String language,

        @NotNull(message = "Duration in minutes is required")
        @Min(value = 30, message = "Duration must be at least 30 minutes")
        @Max(value = 600, message = "Duration must not exceed 600 minutes")
        Integer durationMinutes,

        @NotNull(message = "Release date is required")
        LocalDate releaseDate,

        @Size(max = 20, message = "Age rating must not exceed 20 characters")
        String ageRating,

        @Size(max = 500, message = "Cast details must not exceed 500 characters")
        String castDetails,

        @Size(max = 160, message = "Director name must not exceed 160 characters")
        String director,

        @Size(max = 500, message = "Trailer URL must not exceed 500 characters")
        String trailerUrl,

        String releaseStatus,

        Integer popularityScore,

        Boolean active
) {
}
