package com.cinebook.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueReportRowDto(
        LocalDate bookingDate,
        long totalBookings,
        long confirmedBookings,
        long cancelledBookings,
        long ticketsSold,
        BigDecimal grossSubtotal,
        BigDecimal totalFees,
        BigDecimal confirmedRevenue
) {
}
