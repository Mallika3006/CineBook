package com.cinebook.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record PricePreviewResponse(
        Long showtimeId,
        List<BookingSeatItemDto> seats,
        BigDecimal subtotal,
        BigDecimal convenienceFee,
        BigDecimal gstAmount,
        BigDecimal totalFees,
        BigDecimal totalAmount
) {
}
