package com.cinebook.report.dto;

import java.math.BigDecimal;

public record MoviePerformanceDto(
        Long movieId,
        String movieTitle,
        String genreName,
        String language,
        long totalShowtimes,
        long confirmedBookings,
        long cancelledBookings,
        long ticketsSold,
        BigDecimal confirmedRevenue
) {
}
