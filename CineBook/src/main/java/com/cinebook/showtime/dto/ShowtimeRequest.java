package com.cinebook.showtime.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ShowtimeRequest(
        @NotNull(message = "Movie ID is required")
        Long movieId,

        @NotNull(message = "Screen ID is required")
        Long screenId,

        @NotNull(message = "Start time is required")
        OffsetDateTime startTime,

        @NotBlank(message = "Format is required (e.g., 2D, 3D, IMAX 2D, DOLBY ATMOS)")
        String format,

        @NotNull(message = "Regular ticket price is required")
        @DecimalMin(value = "1.00", message = "Ticket price must be greater than 0")
        BigDecimal ticketPrice,

        @DecimalMin(value = "1.00", message = "Premium price must be greater than 0")
        BigDecimal premiumPrice,

        @DecimalMin(value = "1.00", message = "Recliner price must be greater than 0")
        BigDecimal reclinerPrice
) {
}
