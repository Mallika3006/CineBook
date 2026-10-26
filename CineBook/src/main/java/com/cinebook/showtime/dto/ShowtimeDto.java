package com.cinebook.showtime.dto;

import com.cinebook.showtime.Showtime;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ShowtimeDto(
        Long id,
        Long movieId,
        String movieTitle,
        String moviePosterUrl,
        String movieLanguage,
        int movieDurationMinutes,
        String movieAgeRating,
        Long screenId,
        String screenName,
        int screenCapacity,
        Long theatreId,
        String theatreName,
        String theatreAddress,
        String theatreCity,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String format,
        BigDecimal ticketPrice,
        BigDecimal premiumPrice,
        BigDecimal reclinerPrice,
        String status,
        int availableSeats,
        int bookedSeats,
        String availabilityBadge
) {
    public static ShowtimeDto from(Showtime s) {
        int available = Math.max(0, s.getScreenCapacity() - s.getBookedSeatCount());
        String badge;
        if (!"SCHEDULED".equals(s.getStatus().name()) || s.getStartTime().isBefore(OffsetDateTime.now())) {
            badge = "UNAVAILABLE";
        } else if (available == 0) {
            badge = "SOLD_OUT";
        } else if (s.getScreenCapacity() > 0 && ((double) available / s.getScreenCapacity()) <= 0.25) {
            badge = "FILLING_FAST";
        } else {
            badge = "AVAILABLE";
        }

        return new ShowtimeDto(
                s.getId(),
                s.getMovieId(),
                s.getMovieTitle(),
                s.getMoviePosterUrl(),
                s.getMovieLanguage(),
                s.getMovieDurationMinutes(),
                s.getMovieAgeRating(),
                s.getScreenId(),
                s.getScreenName(),
                s.getScreenCapacity(),
                s.getTheatreId(),
                s.getTheatreName(),
                s.getTheatreAddress(),
                s.getTheatreCity(),
                s.getStartTime(),
                s.getEndTime(),
                s.getFormat(),
                s.getTicketPrice(),
                s.getPremiumPrice(),
                s.getReclinerPrice(),
                s.getStatus().name(),
                available,
                s.getBookedSeatCount(),
                badge
        );
    }
}
