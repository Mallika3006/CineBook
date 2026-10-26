package com.cinebook.seat.dto;

import com.cinebook.seat.Seat;

public record SeatDto(
        Long id,
        Long screenId,
        String rowLabel,
        int seatNumber,
        String seatLabel,
        String seatType,
        boolean active
) {
    public static SeatDto from(Seat s) {
        return new SeatDto(
                s.getId(),
                s.getScreenId(),
                s.getRowLabel(),
                s.getSeatNumber(),
                s.getSeatLabel(),
                s.getSeatType().name(),
                s.isActive()
        );
    }
}
