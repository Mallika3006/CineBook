package com.cinebook.screen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SeatLayoutGeneratorRequest(
        @NotNull(message = "Regular rows count is required")
        @Min(value = 1, message = "Regular rows must be at least 1")
        @Max(value = 15, message = "Regular rows must not exceed 15")
        Integer regularRows,

        @NotNull(message = "Premium rows count is required")
        @Min(value = 0, message = "Premium rows cannot be negative")
        @Max(value = 15, message = "Premium rows must not exceed 15")
        Integer premiumRows,

        @NotNull(message = "Recliner rows count is required")
        @Min(value = 0, message = "Recliner rows cannot be negative")
        @Max(value = 10, message = "Recliner rows must not exceed 10")
        Integer reclinerRows,

        @NotNull(message = "Seats per row is required")
        @Min(value = 4, message = "Seats per row must be at least 4")
        @Max(value = 24, message = "Seats per row must not exceed 24")
        Integer seatsPerRow
) {
}
