package com.cinebook.report;

import com.cinebook.report.dto.DashboardSummaryDto;
import com.cinebook.report.dto.MoviePerformanceDto;
import com.cinebook.report.dto.OccupancyReportRowDto;
import com.cinebook.report.dto.RevenueReportRowDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(reportService.getSummary());
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<RevenueReportRowDto>> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.getRevenueReport(startDate, endDate));
    }

    @GetMapping("/occupancy")
    public ResponseEntity<List<OccupancyReportRowDto>> getOccupancy(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportService.getOccupancyReport(startDate, endDate));
    }

    @GetMapping("/movie-performance")
    public ResponseEntity<List<MoviePerformanceDto>> getMoviePerformance() {
        return ResponseEntity.ok(reportService.getMoviePerformance());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv() {
        String csv = reportService.exportMoviePerformanceCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename='cinebook-movie-performance-report.csv'")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
