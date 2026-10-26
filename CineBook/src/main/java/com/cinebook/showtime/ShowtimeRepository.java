package com.cinebook.showtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ShowtimeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public static final RowMapper<Showtime> SHOWTIME_ROW_MAPPER = (rs, rowNum) -> {
        Showtime s = new Showtime();
        s.setId(rs.getLong("id"));
        s.setMovieId(rs.getLong("movie_id"));
        s.setMovieTitle(rs.getString("movie_title"));
        s.setMoviePosterUrl(rs.getString("movie_poster_url"));
        s.setMovieLanguage(rs.getString("movie_language"));
        s.setMovieDurationMinutes(rs.getInt("movie_duration_minutes"));
        s.setMovieAgeRating(rs.getString("movie_age_rating"));
        s.setScreenId(rs.getLong("screen_id"));
        s.setScreenName(rs.getString("screen_name"));
        s.setScreenCapacity(rs.getInt("screen_capacity"));
        s.setTheatreId(rs.getLong("theatre_id"));
        s.setTheatreName(rs.getString("theatre_name"));
        s.setTheatreAddress(rs.getString("theatre_address"));
        s.setTheatreCity(rs.getString("theatre_city"));

        ZoneId displayZone = ZoneId.of("Asia/Kolkata");
        Timestamp startTs = rs.getTimestamp("start_time");
        Timestamp endTs = rs.getTimestamp("end_time");
        Timestamp createdTs = rs.getTimestamp("created_at");
        if (startTs != null) {
            s.setStartTime(startTs.toInstant().atZone(displayZone).toOffsetDateTime());
        }
        if (endTs != null) {
            s.setEndTime(endTs.toInstant().atZone(displayZone).toOffsetDateTime());
        }
        if (createdTs != null) {
            s.setCreatedAt(createdTs.toInstant().atZone(displayZone).toOffsetDateTime());
        }

        s.setFormat(rs.getString("format"));
        s.setTicketPrice(rs.getBigDecimal("ticket_price"));
        s.setPremiumPrice(rs.getBigDecimal("premium_price"));
        s.setReclinerPrice(rs.getBigDecimal("recliner_price"));
        s.setStatus(ShowtimeStatus.valueOf(rs.getString("status")));
        s.setBookedSeatCount(rs.getInt("booked_seat_count"));
        return s;
    };

    private static final String BASE_SELECT = """
            SELECT st.id, st.movie_id, m.title AS movie_title, m.poster_url AS movie_poster_url,
                   m.language AS movie_language, m.duration_minutes AS movie_duration_minutes,
                   m.age_rating AS movie_age_rating,
                   st.screen_id, sc.name AS screen_name, sc.capacity AS screen_capacity,
                   t.id AS theatre_id, t.name AS theatre_name, t.address AS theatre_address, t.city AS theatre_city,
                   st.start_time, st.end_time, st.format,
                   st.ticket_price, st.premium_price, st.recliner_price, st.status, st.created_at,
                   (SELECT COUNT(*) FROM booking_seats bs WHERE bs.showtime_id = st.id AND bs.status = 'ACTIVE') AS booked_seat_count
            FROM showtimes st
            JOIN movies m ON m.id = st.movie_id
            JOIN screens sc ON sc.id = st.screen_id
            JOIN theatres t ON t.id = sc.theatre_id
            """;

    public ShowtimeRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
    }

    public List<Showtime> findShowtimes(
            LocalDate date,
            String city,
            Long movieId,
            Long theatreId,
            String status,
            ZoneId zoneId
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (date != null) {
            ZonedDateTime dayStart = date.atStartOfDay(zoneId);
            ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(zoneId);
            where.append(" AND st.start_time >= :dayStart AND st.start_time < :dayEnd ");
            params.addValue("dayStart", Timestamp.from(dayStart.toInstant()));
            params.addValue("dayEnd", Timestamp.from(dayEnd.toInstant()));
        }
        if (city != null && !city.isBlank()) {
            where.append(" AND LOWER(t.city) = LOWER(:city) ");
            params.addValue("city", city.trim());
        }
        if (movieId != null) {
            where.append(" AND st.movie_id = :movieId ");
            params.addValue("movieId", movieId);
        }
        if (theatreId != null) {
            where.append(" AND t.id = :theatreId ");
            params.addValue("theatreId", theatreId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND st.status = :status ");
            params.addValue("status", status.trim().toUpperCase());
        }

        String sql = BASE_SELECT + where + " ORDER BY st.start_time ASC, t.name ASC, sc.name ASC";
        return namedJdbc.query(sql, params, SHOWTIME_ROW_MAPPER);
    }

    public Optional<Showtime> findById(Long id) {
        List<Showtime> list = jdbcTemplate.query(BASE_SELECT + " WHERE st.id = ?", SHOWTIME_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Showtime> findByIdForUpdate(Long id) {
        String sql = BASE_SELECT + " WHERE st.id = ? FOR UPDATE OF st";
        List<Showtime> list = jdbcTemplate.query(sql, SHOWTIME_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /**
     * Checks if any SCHEDULED showtime overlaps [startTime, endTime) on the given screen.
     */
    public boolean hasOverlappingShowtime(Long screenId, OffsetDateTime startTime, OffsetDateTime endTime, Long excludeShowtimeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM showtimes
                WHERE screen_id = :screenId
                  AND status = 'SCHEDULED'
                  AND start_time < :endTime
                  AND end_time > :startTime
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("screenId", screenId)
                .addValue("startTime", Timestamp.from(startTime.toInstant()))
                .addValue("endTime", Timestamp.from(endTime.toInstant()));

        if (excludeShowtimeId != null) {
            sql.append(" AND id <> :excludeId ");
            params.addValue("excludeId", excludeShowtimeId);
        }

        Integer count = namedJdbc.queryForObject(sql.toString(), params, Integer.class);
        return count != null && count > 0;
    }

    public Showtime save(Showtime s) {
        String sql = """
                INSERT INTO showtimes (
                    movie_id, screen_id, start_time, end_time, format,
                    ticket_price, premium_price, recliner_price, status, created_at
                ) VALUES (
                    :movieId, :screenId, :startTime, :endTime, :format,
                    :ticketPrice, :premiumPrice, :reclinerPrice, :status, NOW()
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("movieId", s.getMovieId())
                .addValue("screenId", s.getScreenId())
                .addValue("startTime", Timestamp.from(s.getStartTime().toInstant()))
                .addValue("endTime", Timestamp.from(s.getEndTime().toInstant()))
                .addValue("format", s.getFormat())
                .addValue("ticketPrice", s.getTicketPrice())
                .addValue("premiumPrice", s.getPremiumPrice())
                .addValue("reclinerPrice", s.getReclinerPrice())
                .addValue("status", s.getStatus().name());

        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbc.update(sql, params, kh, new String[]{"id"});
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return findById(id).orElseThrow();
    }

    public Showtime update(Long id, Showtime s) {
        String sql = """
                UPDATE showtimes SET
                    movie_id = :movieId,
                    screen_id = :screenId,
                    start_time = :startTime,
                    end_time = :endTime,
                    format = :format,
                    ticket_price = :ticketPrice,
                    premium_price = :premiumPrice,
                    recliner_price = :reclinerPrice
                WHERE id = :id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("movieId", s.getMovieId())
                .addValue("screenId", s.getScreenId())
                .addValue("startTime", Timestamp.from(s.getStartTime().toInstant()))
                .addValue("endTime", Timestamp.from(s.getEndTime().toInstant()))
                .addValue("format", s.getFormat())
                .addValue("ticketPrice", s.getTicketPrice())
                .addValue("premiumPrice", s.getPremiumPrice())
                .addValue("reclinerPrice", s.getReclinerPrice());

        namedJdbc.update(sql, params);
        return findById(id).orElseThrow();
    }

    public void updateStatus(Long id, ShowtimeStatus status) {
        jdbcTemplate.update("UPDATE showtimes SET status = ? WHERE id = ?", status.name(), id);
    }
}
