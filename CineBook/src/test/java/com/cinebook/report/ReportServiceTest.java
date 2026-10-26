package com.cinebook.report;

import com.cinebook.config.AppProperties;
import com.cinebook.report.dto.DashboardSummaryDto;
import com.cinebook.report.dto.MoviePerformanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setTimezone("Asia/Kolkata");
        reportService = new ReportService(reportRepository, props);
    }

    @Test
    @DisplayName("Dashboard summary returns accurate totals and occupancy percentage")
    void dashboardSummaryReturnsMetrics() {
        DashboardSummaryDto mockSummary = new DashboardSummaryDto(
                10L, 10L, 6L, 48L, 15L, 12L, 3L, 34L,
                new BigDecimal("11420.50"),
                new BigDecimal("18.75")
        );
        when(reportRepository.fetchDashboardSummary()).thenReturn(mockSummary);

        DashboardSummaryDto actual = reportService.getSummary();

        assertThat(actual.totalMovies()).isEqualTo(10L);
        assertThat(actual.confirmedBookings()).isEqualTo(12L);
        assertThat(actual.cancelledBookings()).isEqualTo(3L);
        assertThat(actual.confirmedBookingValue()).isEqualByComparingTo("11420.50");
    }

    @Test
    @DisplayName("CSV export formats headers and escapes movie titles containing commas")
    void exportMoviePerformanceCsvFormatsCleanly() {
        when(reportRepository.fetchMoviePerformance()).thenReturn(List.of(
                new MoviePerformanceDto(1L, "Chronos Protocol", "Sci-Fi", "English", 14L, 8L, 1L, 22L, new BigDecimal("8450.00")),
                new MoviePerformanceDto(2L, "Kaalacakra, Part I", "Action", "Kannada", 10L, 5L, 0L, 12L, new BigDecimal("4200.00"))
        ));

        String csv = reportService.exportMoviePerformanceCsv();

        assertThat(csv).startsWith("Movie ID,Movie Title,Genre,Language,Total Showtimes,Confirmed Bookings,Cancelled Bookings,Tickets Sold,Confirmed Booking Value (INR)");
        assertThat(csv).contains("1,Chronos Protocol,Sci-Fi,English,14,8,1,22,8450.00");
        assertThat(csv).contains(
                "2,\"Kaalacakra, Part I\",Action,Kannada,10,5,0,12,4200.00"
        );
    }
}
