package com.cinebook.movie.dto;

import com.cinebook.movie.Movie;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MovieDto(
        Long id,
        String title,
        String description,
        String posterUrl,
        String backdropUrl,
        Long genreId,
        String genreName,
        String language,
        int durationMinutes,
        LocalDate releaseDate,
        String ageRating,
        String castDetails,
        String director,
        String trailerUrl,
        String releaseStatus,
        int popularityScore,
        boolean active,
        OffsetDateTime createdAt
) {
    public static MovieDto from(Movie m) {
        return new MovieDto(
                m.getId(),
                m.getTitle(),
                m.getDescription(),
                m.getPosterUrl(),
                m.getBackdropUrl(),
                m.getGenreId(),
                m.getGenreName(),
                m.getLanguage(),
                m.getDurationMinutes(),
                m.getReleaseDate(),
                m.getAgeRating(),
                m.getCastDetails(),
                m.getDirector(),
                m.getTrailerUrl(),
                m.getReleaseStatus(),
                m.getPopularityScore(),
                m.isActive(),
                m.getCreatedAt()
        );
    }
}
