package com.cinebook.booking.dto;

import com.cinebook.booking.BookingSeat;
import java.math.BigDecimal;

public record BookingSeatItemDto(
        Long seatId,
        String rowLabel,
        int seatNumber,
        String seatLabel,
        String seatType,
        BigDecimal priceAtBooking,
        String status
) {
    public static BookingSeatItemDto from(BookingSeat bs) {
        return new BookingSeatItemDto(
                bs.getSeatId(),
                bs.getRowLabel(),
                bs.getSeatNumber(),
                bs.getSeatLabel(),
                bs.getSeatType(),
                bs.getPriceAtBooking(),
                bs.getStatus().name()
        );
    }
}
