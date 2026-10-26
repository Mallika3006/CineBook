package com.cinebook.report;

import com.cinebook.config.AppProperties;
import com.cinebook.report.dto.DashboardSummaryDto;
import com.cinebook.report.dto.MoviePerformanceDto;
import com.cinebook.report.dto.OccupancyReportRowDto;
import com.cinebook.report.dto.RevenueReportRowDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final AppProperties appProperties;

    public ReportService(ReportRepository reportRepository, AppProperties appProperties) {
        this.reportRepository = reportRepository;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary() {
        return reportRepository.fetchDashboardSummary();
    }

    @Transactional(readOnly = true)
    public List<RevenueReportRowDto> getRevenueReport(LocalDate startDate, LocalDate endDate) {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        LocalDate safeEnd = endDate != null ? endDate : LocalDate.now(zoneId).plusDays(7);
        LocalDate safeStart = startDate != null ? startDate : safeEnd.minusDays(30);
        return reportRepository.fetchRevenueByDate(safeStart, safeEnd, zoneId);
    }

    @Transactional(readOnly = true)
    public List<OccupancyReportRowDto> getOccupancyReport(LocalDate startDate, LocalDate endDate) {
        ZoneId zoneId = ZoneId.of(appProperties.getTimezone());
        LocalDate safeStart = startDate != null ? startDate : LocalDate.now(zoneId).minusDays(7);
        LocalDate safeEnd = endDate != null ? endDate : LocalDate.now(zoneId).plusDays(14);
        return reportRepository.fetchOccupancyReport(safeStart, safeEnd, zoneId);
    }

    @Transactional(readOnly = true)
    public List<MoviePerformanceDto> getMoviePerformance() {
        return reportRepository.fetchMoviePerformance();
    }

    @Transactional(readOnly = true)
    public String exportMoviePerformanceCsv() {
        List<MoviePerformanceDto> rows = reportRepository.fetchMoviePerformance();
        StringBuilder sb = new StringBuilder();
        sb.append("Movie ID,Movie Title,Genre,Language,Total Showtimes,Confirmed Bookings,Cancelled Bookings,Tickets Sold,Confirmed Booking Value (INR)");
        for (MoviePerformanceDto r : rows) {
            sb.append(r.movieId()).append(',')
              .append(escapeCsv(r.movieTitle())).append(',')
              .append(escapeCsv(r.genreName())).append(',')
              .append(escapeCsv(r.language())).append(',')
              .append(r.totalShowtimes()).append(',')
              .append(r.confirmedBookings()).append(',')
              .append(r.cancelledBookings()).append(',')
              .append(r.ticketsSold()).append(',')
              .append(r.confirmedRevenue()).append(' ');
        }
        return sb.toString();
    }

    private String escapeCsv(String input) {
        if (input == null) {
            return "";
        }
        if (input.contains(",") || input.contains("\"") || input.contains("\n")) {
            return "\"" + input.replace("\"", "\"\"") + "\"";
        }
        return input;
    }
}
