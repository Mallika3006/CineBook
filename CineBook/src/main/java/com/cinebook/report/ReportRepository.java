package com.cinebook.report;

import com.cinebook.report.dto.DashboardSummaryDto;
import com.cinebook.report.dto.MoviePerformanceDto;
import com.cinebook.report.dto.OccupancyReportRowDto;
import com.cinebook.report.dto.RevenueReportRowDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public ReportRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
    }

    public DashboardSummaryDto fetchDashboardSummary() {
        long totalMovies = queryLong("SELECT COUNT(*) FROM movies");
        long activeMovies = queryLong("SELECT COUNT(*) FROM movies WHERE is_active = TRUE");
        long totalTheatres = queryLong("SELECT COUNT(*) FROM theatres WHERE is_active = TRUE");
        long activeShowtimes = queryLong("SELECT COUNT(*) FROM showtimes WHERE status = 'SCHEDULED' AND start_time > NOW()");
        long totalBookings = queryLong("SELECT COUNT(*) FROM bookings");
        long confirmedBookings = queryLong("SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED'");
        long cancelledBookings = queryLong("SELECT COUNT(*) FROM bookings WHERE status = 'CANCELLED'");
        long confirmedSeatsSold = queryLong("SELECT COUNT(*) FROM booking_seats WHERE status = 'ACTIVE'");

        BigDecimal confirmedValue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0.00) FROM bookings WHERE status = 'CONFIRMED'",
                BigDecimal.class
        );
        if (confirmedValue == null) {
            confirmedValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Long totalScheduledCapacity = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(sc.capacity), 0)
                FROM showtimes st
                JOIN screens sc ON sc.id = st.screen_id
                WHERE st.status = 'SCHEDULED'
                """,
                Long.class
        );

        BigDecimal occupancyPct = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (totalScheduledCapacity != null && totalScheduledCapacity > 0) {
            occupancyPct = BigDecimal.valueOf(confirmedSeatsSold * 100.0)
                    .divide(BigDecimal.valueOf(totalScheduledCapacity), 2, RoundingMode.HALF_UP);
        }

        return new DashboardSummaryDto(
                totalMovies,
                activeMovies,
                totalTheatres,
                activeShowtimes,
                totalBookings,
                confirmedBookings,
                cancelledBookings,
                confirmedSeatsSold,
                confirmedValue.setScale(2, RoundingMode.HALF_UP),
                occupancyPct
        );
    }

    public List<RevenueReportRowDto> fetchRevenueByDate(LocalDate startDate, LocalDate endDate, ZoneId zoneId) {
        ZonedDateTime startZdt = startDate.atStartOfDay(zoneId);
        ZonedDateTime endZdt = endDate.plusDays(1).atStartOfDay(zoneId);

        String sql = """
                SELECT
                    CAST(b.created_at AT TIME ZONE :tz AS DATE) AS booking_date,
                    COUNT(b.id) AS total_bookings,
                    COUNT(b.id) FILTER (WHERE b.status = 'CONFIRMED') AS confirmed_bookings,
                    COUNT(b.id) FILTER (WHERE b.status = 'CANCELLED') AS cancelled_bookings,
                    COALESCE((
                        SELECT COUNT(*)
                        FROM booking_seats bs
                        JOIN bookings b2 ON b2.id = bs.booking_id
                        WHERE CAST(b2.created_at AT TIME ZONE :tz AS DATE) = CAST(b.created_at AT TIME ZONE :tz AS DATE)
                          AND b2.status = 'CONFIRMED'
                          AND bs.status = 'ACTIVE'
                    ), 0) AS tickets_sold,
                    COALESCE(SUM(b.subtotal) FILTER (WHERE b.status = 'CONFIRMED'), 0.00) AS gross_subtotal,
                    COALESCE(SUM(b.fees) FILTER (WHERE b.status = 'CONFIRMED'), 0.00) AS total_fees,
                    COALESCE(SUM(b.total_amount) FILTER (WHERE b.status = 'CONFIRMED'), 0.00) AS confirmed_revenue
                FROM bookings b
                WHERE b.created_at >= :startTs AND b.created_at < :endTs
                GROUP BY CAST(b.created_at AT TIME ZONE :tz AS DATE)
                ORDER BY booking_date DESC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tz", zoneId.getId())
                .addValue("startTs", Timestamp.from(startZdt.toInstant()))
                .addValue("endTs", Timestamp.from(endZdt.toInstant()));

        return namedJdbc.query(sql, params, (rs, rowNum) -> {
            Date d = rs.getDate("booking_date");
            return new RevenueReportRowDto(
                    d != null ? d.toLocalDate() : LocalDate.now(),
                    rs.getLong("total_bookings"),
                    rs.getLong("confirmed_bookings"),
                    rs.getLong("cancelled_bookings"),
                    rs.getLong("tickets_sold"),
                    rs.getBigDecimal("gross_subtotal"),
                    rs.getBigDecimal("total_fees"),
                    rs.getBigDecimal("confirmed_revenue")
            );
        });
    }

    public List<OccupancyReportRowDto> fetchOccupancyReport(LocalDate startDate, LocalDate endDate, ZoneId zoneId) {
        ZonedDateTime startZdt = startDate.atStartOfDay(zoneId);
        ZonedDateTime endZdt = endDate.plusDays(1).atStartOfDay(zoneId);

        String sql = """
                SELECT
                    st.id AS showtime_id,
                    m.title AS movie_title,
                    t.name AS theatre_name,
                    t.city AS city,
                    sc.name AS screen_name,
                    st.start_time,
                    st.format,
                    sc.capacity AS screen_capacity,
                    COUNT(bs.id) FILTER (WHERE bs.status = 'ACTIVE') AS seats_sold
                FROM showtimes st
                JOIN movies m ON m.id = st.movie_id
                JOIN screens sc ON sc.id = st.screen_id
                JOIN theatres t ON t.id = sc.theatre_id
                LEFT JOIN booking_seats bs ON bs.showtime_id = st.id AND bs.status = 'ACTIVE'
                WHERE st.start_time >= :startTs AND st.start_time < :endTs
                  AND st.status = 'SCHEDULED'
                GROUP BY st.id, m.title, t.name, t.city, sc.name, st.start_time, st.format, sc.capacity
                ORDER BY seats_sold DESC, st.start_time ASC
                LIMIT 100
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startTs", Timestamp.from(startZdt.toInstant()))
                .addValue("endTs", Timestamp.from(endZdt.toInstant()));

        return namedJdbc.query(sql, params, (rs, rowNum) -> {
            int cap = rs.getInt("screen_capacity");
            int sold = rs.getInt("seats_sold");
            int avail = Math.max(0, cap - sold);
            BigDecimal pct = cap > 0
                    ? BigDecimal.valueOf(sold * 100.0).divide(BigDecimal.valueOf(cap), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Timestamp stTs = rs.getTimestamp("start_time");
            return new OccupancyReportRowDto(
                    rs.getLong("showtime_id"),
                    rs.getString("movie_title"),
                    rs.getString("theatre_name"),
                    rs.getString("city"),
                    rs.getString("screen_name"),
                    stTs != null ? stTs.toInstant().atZone(zoneId).toOffsetDateTime() : null,
                    rs.getString("format"),
                    cap,
                    sold,
                    avail,
                    pct
            );
        });
    }

    public List<MoviePerformanceDto> fetchMoviePerformance() {
        String sql = """
                SELECT
                    m.id AS movie_id,
                    m.title AS movie_title,
                    g.name AS genre_name,
                    m.language,
                    COUNT(DISTINCT st.id) AS total_showtimes,
                    COUNT(DISTINCT b.id) FILTER (WHERE b.status = 'CONFIRMED') AS confirmed_bookings,
                    COUNT(DISTINCT b.id) FILTER (WHERE b.status = 'CANCELLED') AS cancelled_bookings,
                    COUNT(bs.id) FILTER (WHERE bs.status = 'ACTIVE') AS tickets_sold,
                    COALESCE((
                        SELECT SUM(b2.total_amount)
                        FROM bookings b2
                        JOIN showtimes st2 ON st2.id = b2.showtime_id
                        WHERE st2.movie_id = m.id AND b2.status = 'CONFIRMED'
                    ), 0.00) AS confirmed_revenue
                FROM movies m
                JOIN genres g ON g.id = m.genre_id
                LEFT JOIN showtimes st ON st.movie_id = m.id
                LEFT JOIN bookings b ON b.showtime_id = st.id
                LEFT JOIN booking_seats bs ON bs.booking_id = b.id AND bs.status = 'ACTIVE'
                GROUP BY m.id, m.title, g.name, m.language
                ORDER BY confirmed_revenue DESC, tickets_sold DESC, m.title ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new MoviePerformanceDto(
                rs.getLong("movie_id"),
                rs.getString("movie_title"),
                rs.getString("genre_name"),
                rs.getString("language"),
                rs.getLong("total_showtimes"),
                rs.getLong("confirmed_bookings"),
                rs.getLong("cancelled_bookings"),
                rs.getLong("tickets_sold"),
                rs.getBigDecimal("confirmed_revenue")
        ));
    }

    private long queryLong(String sql) {
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0L;
    }
}
