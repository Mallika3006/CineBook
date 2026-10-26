package com.cinebook.seat.dto;

import com.cinebook.showtime.dto.ShowtimeDto;
import java.math.BigDecimal;
import java.util.List;

public record ShowtimeSeatMapResponse(
        ShowtimeDto showtime,
        int maxSeatsPerBooking,
        BigDecimal convenienceFeePercent,
        BigDecimal gstOnFeePercent,
        List<ShowtimeSeatDto> seats
) {
}
