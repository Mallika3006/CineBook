package com.cinebook.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SeatHoldRequest(
        @NotNull(message = "Showtime ID is required")
        Long showtimeId,

        @NotEmpty(message = "At least one seat must be selected")
        @Size(max = 10, message = "Cannot hold more than 10 seats")
        List<Long> seatIds
) {
}
