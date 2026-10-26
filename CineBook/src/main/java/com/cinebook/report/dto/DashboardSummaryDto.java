package com.cinebook.report.dto;

import java.math.BigDecimal;

/**
 * Real database-derived statistics for the Admin Overview & Reports page.
 * Each metric is explicitly defined:
 * - totalMovies: Count of all movies in the catalog
 * - activeMovies: Count of movies with is_active = TRUE
 * - activeShowtimes: Count of SCHEDULED showtimes with start_time > NOW()
 * - totalBookings: Count of all bookings (CONFIRMED + CANCELLED)
 * - confirmedBookings: Count of CONFIRMED bookings
 * - cancelledBookings: Count of CANCELLED bookings
 * - confirmedSeatsSold: Count of ACTIVE rows in booking_seats
 * - confirmedBookingValue: Sum of total_amount across all CONFIRMED bookings (Demo Checkout Confirmed Value)
 * - overallOccupancyPercent: Percentage of confirmed seats sold across all scheduled showtimes
 */
public record DashboardSummaryDto(
        long totalMovies,
        long activeMovies,
        long totalTheatres,
        long activeShowtimes,
        long totalBookings,
        long confirmedBookings,
        long cancelledBookings,
        long confirmedSeatsSold,
        BigDecimal confirmedBookingValue,
        BigDecimal overallOccupancyPercent
) {
}
