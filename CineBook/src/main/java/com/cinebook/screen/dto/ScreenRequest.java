package com.cinebook.screen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScreenRequest(
        @NotNull(message = "Theatre ID is required")
        Long theatreId,

        @NotBlank(message = "Screen name is required")
        @Size(max = 100, message = "Screen name must not exceed 100 characters")
        String name,

        @Min(value = 1, message = "Regular rows must be at least 1")
        @Max(value = 15, message = "Regular rows must not exceed 15")
        Integer regularRows,

        @Min(value = 0, message = "Premium rows cannot be negative")
        @Max(value = 15, message = "Premium rows must not exceed 15")
        Integer premiumRows,

        @Min(value = 0, message = "Recliner rows cannot be negative")
        @Max(value = 10, message = "Recliner rows must not exceed 10")
        Integer reclinerRows,

        @Min(value = 4, message = "Seats per row must be at least 4")
        @Max(value = 24, message = "Seats per row must not exceed 24")
        Integer seatsPerRow,

        Boolean active
) {
}
