package com.cinebook.report.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OccupancyReportRowDto(
        Long showtimeId,
        String movieTitle,
        String theatreName,
        String city,
        String screenName,
        OffsetDateTime startTime,
        String format,
        int screenCapacity,
        int seatsSold,
        int availableSeats,
        BigDecimal occupancyPercent
) {
}
