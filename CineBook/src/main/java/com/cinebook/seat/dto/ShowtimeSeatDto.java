package com.cinebook.seat.dto;

import java.math.BigDecimal;

public record ShowtimeSeatDto(
        Long id,
        Long screenId,
        String rowLabel,
        int seatNumber,
        String seatLabel,
        String seatType,
        BigDecimal price,
        String availabilityStatus
) {
}
