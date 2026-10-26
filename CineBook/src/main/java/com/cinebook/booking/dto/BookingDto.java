package com.cinebook.booking.dto;

import com.cinebook.booking.Booking;
import com.cinebook.payment.dto.PaymentDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingDto(
        Long id,
        String bookingReference,
        Long userId,
        String customerName,
        String customerEmail,
        Long showtimeId,
        Long movieId,
        String movieTitle,
        String moviePosterUrl,
        String movieLanguage,
        String movieAgeRating,
        String format,
        Long theatreId,
        String theatreName,
        String theatreAddress,
        String theatreCity,
        String screenName,
        OffsetDateTime showStartTime,
        OffsetDateTime showEndTime,
        String status,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal totalAmount,
        OffsetDateTime createdAt,
        OffsetDateTime cancelledAt,
        boolean cancellable,
        List<BookingSeatItemDto> seats,
        PaymentDto payment
) {
    public static BookingDto from(Booking b, boolean cancellable, List<BookingSeatItemDto> seats, PaymentDto payment) {
        return new BookingDto(
                b.getId(),
                b.getBookingReference(),
                b.getUserId(),
                b.getCustomerName(),
                b.getCustomerEmail(),
                b.getShowtimeId(),
                b.getMovieId(),
                b.getMovieTitle(),
                b.getMoviePosterUrl(),
                b.getMovieLanguage(),
                b.getMovieAgeRating(),
                b.getFormat(),
                b.getTheatreId(),
                b.getTheatreName(),
                b.getTheatreAddress(),
                b.getTheatreCity(),
                b.getScreenName(),
                b.getShowStartTime(),
                b.getShowEndTime(),
                b.getStatus().name(),
                b.getSubtotal(),
                b.getFees(),
                b.getTotalAmount(),
                b.getCreatedAt(),
                b.getCancelledAt(),
                cancellable,
                seats,
                payment
        );
    }
}
