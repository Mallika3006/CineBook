package com.cinebook.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Non-sensitive ticket representation with a verification token for QR code rendering.
 * Never exposes passwords, JWT tokens, or card numbers.
 */
public record TicketDto(
        Long bookingId,
        String bookingReference,
        String verificationToken,
        String qrPayload,
        String status,
        String customerName,
        String movieTitle,
        String moviePosterUrl,
        String movieLanguage,
        String movieAgeRating,
        String format,
        String theatreName,
        String theatreAddress,
        String theatreCity,
        String screenName,
        OffsetDateTime showStartTime,
        OffsetDateTime showEndTime,
        List<BookingSeatItemDto> seats,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal totalAmount,
        String paymentProvider,
        String paymentReference,
        String paymentStatus,
        OffsetDateTime issuedAt
) {
}
