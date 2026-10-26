package com.cinebook.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(
        @NotNull(message = "Showtime ID is required")
        Long showtimeId,

        @NotEmpty(message = "At least one seat must be selected")
        @Size(max = 10, message = "Cannot book more than 10 seats in a single booking")
        List<Long> seatIds,

        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey
) {
}
