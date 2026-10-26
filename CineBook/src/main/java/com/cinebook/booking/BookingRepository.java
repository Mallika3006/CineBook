package com.cinebook.booking;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public static final RowMapper<Booking> BOOKING_ROW_MAPPER = (rs, rowNum) -> {
        Booking b = new Booking();
        b.setId(rs.getLong("id"));
        b.setBookingReference(rs.getString("booking_reference"));
        b.setVerificationToken(rs.getString("verification_token"));
        b.setUserId(rs.getLong("user_id"));
        b.setCustomerName(rs.getString("customer_name"));
        b.setCustomerEmail(rs.getString("customer_email"));
        b.setShowtimeId(rs.getLong("showtime_id"));
        b.setMovieId(rs.getLong("movie_id"));
        b.setMovieTitle(rs.getString("movie_title"));
        b.setMoviePosterUrl(rs.getString("movie_poster_url"));
        b.setMovieLanguage(rs.getString("movie_language"));
        b.setMovieAgeRating(rs.getString("movie_age_rating"));
        b.setFormat(rs.getString("format"));
        b.setTheatreId(rs.getLong("theatre_id"));
        b.setTheatreName(rs.getString("theatre_name"));
        b.setTheatreAddress(rs.getString("theatre_address"));
        b.setTheatreCity(rs.getString("theatre_city"));
        b.setScreenName(rs.getString("screen_name"));

        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        Timestamp startTs = rs.getTimestamp("show_start_time");
        Timestamp endTs = rs.getTimestamp("show_end_time");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp cancelledTs = rs.getTimestamp("cancelled_at");
        if (startTs != null) {
            b.setShowStartTime(startTs.toInstant().atZone(zoneId).toOffsetDateTime());
        }
        if (endTs != null) {
            b.setShowEndTime(endTs.toInstant().atZone(zoneId).toOffsetDateTime());
        }
        if (createdTs != null) {
            b.setCreatedAt(createdTs.toInstant().atZone(zoneId).toOffsetDateTime());
        }
        if (cancelledTs != null) {
            b.setCancelledAt(cancelledTs.toInstant().atZone(zoneId).toOffsetDateTime());
        }

        b.setStatus(BookingStatus.valueOf(rs.getString("status")));
        b.setSubtotal(rs.getBigDecimal("subtotal"));
        b.setFees(rs.getBigDecimal("fees"));
        b.setTotalAmount(rs.getBigDecimal("total_amount"));
        b.setIdempotencyKey(rs.getString("idempotency_key"));
        return b;
    };

    public static final RowMapper<BookingSeat> BOOKING_SEAT_ROW_MAPPER = (rs, rowNum) -> {
        BookingSeat bs = new BookingSeat();
        bs.setId(rs.getLong("id"));
        bs.setBookingId(rs.getLong("booking_id"));
        bs.setShowtimeId(rs.getLong("showtime_id"));
        bs.setSeatId(rs.getLong("seat_id"));
        bs.setRowLabel(rs.getString("row_label"));
        bs.setSeatNumber(rs.getInt("seat_number"));
        bs.setSeatType(rs.getString("seat_type"));
        bs.setPriceAtBooking(rs.getBigDecimal("price_at_booking"));
        bs.setStatus(BookingSeatStatus.valueOf(rs.getString("status")));
        Timestamp cTs = rs.getTimestamp("created_at");
        if (cTs != null) {
            bs.setCreatedAt(cTs.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime());
        }
        return bs;
    };

    private static final String BASE_BOOKING_SELECT = """
            SELECT b.id, b.booking_reference, b.verification_token, b.user_id,
                   u.full_name AS customer_name, u.email AS customer_email,
                   b.showtime_id, m.id AS movie_id, m.title AS movie_title,
                   m.poster_url AS movie_poster_url, m.language AS movie_language,
                   m.age_rating AS movie_age_rating, st.format,
                   t.id AS theatre_id, t.name AS theatre_name, t.address AS theatre_address,
                   t.city AS theatre_city, sc.name AS screen_name,
                   st.start_time AS show_start_time, st.end_time AS show_end_time,
                   b.status, b.subtotal, b.fees, b.total_amount, b.idempotency_key,
                   b.created_at, b.cancelled_at
            FROM bookings b
            JOIN users u ON u.id = b.user_id
            JOIN showtimes st ON st.id = b.showtime_id
            JOIN movies m ON m.id = st.movie_id
            JOIN screens sc ON sc.id = st.screen_id
            JOIN theatres t ON t.id = sc.theatre_id
            """;

    public BookingRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
    }

    public Set<Long> findActiveBookedSeatIds(Long showtimeId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT seat_id FROM booking_seats WHERE showtime_id = ? AND status = 'ACTIVE'",
                Long.class,
                showtimeId
        );
        return new HashSet<>(ids);
    }

    public Set<Long> findActiveBookedSeatIdsInList(Long showtimeId, List<Long> seatIds) {
        String sql = """
                SELECT seat_id
                FROM booking_seats
                WHERE showtime_id = :showtimeId
                  AND seat_id IN (:seatIds)
                  AND status = 'ACTIVE'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("showtimeId", showtimeId)
                .addValue("seatIds", seatIds);
        return new HashSet<>(namedJdbc.queryForList(sql, params, Long.class));
    }

    public Set<Long> findActiveHeldSeatIds(Long showtimeId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT seat_id FROM seat_holds WHERE showtime_id = ? AND expires_at > NOW()",
                Long.class,
                showtimeId
        );
        return new HashSet<>(ids);
    }

    public Set<Long> findSeatIdsHeldByOtherUsers(Long showtimeId, List<Long> seatIds, Long currentUserId) {
        String sql = """
                SELECT seat_id
                FROM seat_holds
                WHERE showtime_id = :showtimeId
                  AND seat_id IN (:seatIds)
                  AND user_id <> :userId
                  AND expires_at > NOW()
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("showtimeId", showtimeId)
                .addValue("seatIds", seatIds)
                .addValue("userId", currentUserId);
        return new HashSet<>(namedJdbc.queryForList(sql, params, Long.class));
    }

    public void cleanupExpiredHolds(Long showtimeId) {
        jdbcTemplate.update(
                "DELETE FROM seat_holds WHERE showtime_id = ? AND expires_at <= NOW()",
                showtimeId
        );
    }

    public void releaseUserHolds(Long showtimeId, Long userId) {
        jdbcTemplate.update(
                "DELETE FROM seat_holds WHERE showtime_id = ? AND user_id = ?",
                showtimeId,
                userId
        );
    }

    public void createSeatHolds(Long showtimeId, Long userId, List<Long> seatIds, OffsetDateTime expiresAt) {
        String sql = """
                INSERT INTO seat_holds (showtime_id, seat_id, user_id, expires_at, created_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (showtime_id, seat_id)
                DO UPDATE SET user_id = EXCLUDED.user_id, expires_at = EXCLUDED.expires_at, created_at = NOW()
                """;
        Timestamp expTs = Timestamp.from(expiresAt.toInstant());
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, showtimeId);
                ps.setLong(2, seatIds.get(i));
                ps.setLong(3, userId);
                ps.setTimestamp(4, expTs);
            }

            @Override
            public int getBatchSize() {
                return seatIds.size();
            }
        });
    }

    public Optional<Booking> findByUserAndIdempotencyKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String sql = BASE_BOOKING_SELECT + " WHERE b.user_id = ? AND b.idempotency_key = ?";
        List<Booking> list = jdbcTemplate.query(sql, BOOKING_ROW_MAPPER, userId, idempotencyKey.trim());
        return list.stream().findFirst();
    }

    public Optional<Booking> findById(Long id) {
        List<Booking> list = jdbcTemplate.query(BASE_BOOKING_SELECT + " WHERE b.id = ?", BOOKING_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Booking> findByIdForUpdate(Long id) {
        String sql = BASE_BOOKING_SELECT + " WHERE b.id = ? FOR UPDATE OF b";
        List<Booking> list = jdbcTemplate.query(sql, BOOKING_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Booking> findByVerificationToken(String token) {
        List<Booking> list = jdbcTemplate.query(
                BASE_BOOKING_SELECT + " WHERE b.verification_token = ?",
                BOOKING_ROW_MAPPER,
                token
        );
        return list.stream().findFirst();
    }

    public List<Booking> findByUserId(Long userId) {
        String sql = BASE_BOOKING_SELECT + " WHERE b.user_id = ? ORDER BY b.created_at DESC";
        return jdbcTemplate.query(sql, BOOKING_ROW_MAPPER, userId);
    }

    public List<Booking> searchAdminBookings(
            String reference,
            LocalDate date,
            Long movieId,
            Long theatreId,
            String status,
            ZoneId zoneId
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (reference != null && !reference.isBlank()) {
            where.append(" AND (UPPER(b.booking_reference) LIKE :ref OR LOWER(u.email) LIKE :emailRef) ");
            params.addValue("ref", "%" + reference.trim().toUpperCase() + "%");
            params.addValue("emailRef", "%" + reference.trim().toLowerCase() + "%");
        }
        if (date != null) {
            ZonedDateTime start = date.atStartOfDay(zoneId);
            ZonedDateTime end = date.plusDays(1).atStartOfDay(zoneId);
            where.append(" AND b.created_at >= :start AND b.created_at < :end ");
            params.addValue("start", Timestamp.from(start.toInstant()));
            params.addValue("end", Timestamp.from(end.toInstant()));
        }
        if (movieId != null) {
            where.append(" AND m.id = :movieId ");
            params.addValue("movieId", movieId);
        }
        if (theatreId != null) {
            where.append(" AND t.id = :theatreId ");
            params.addValue("theatreId", theatreId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = :status ");
            params.addValue("status", status.trim().toUpperCase());
        }

        String sql = BASE_BOOKING_SELECT + where + " ORDER BY b.created_at DESC LIMIT 250";
        return namedJdbc.query(sql, params, BOOKING_ROW_MAPPER);
    }

    public Booking insertBooking(Booking b) {
        String sql = """
                INSERT INTO bookings (
                    booking_reference, verification_token, user_id, showtime_id,
                    status, subtotal, fees, total_amount, idempotency_key, created_at
                ) VALUES (
                    :bookingReference, :verificationToken, :userId, :showtimeId,
                    :status, :subtotal, :fees, :totalAmount, :idempotencyKey, NOW()
                )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bookingReference", b.getBookingReference())
                .addValue("verificationToken", b.getVerificationToken())
                .addValue("userId", b.getUserId())
                .addValue("showtimeId", b.getShowtimeId())
                .addValue("status", b.getStatus().name())
                .addValue("subtotal", b.getSubtotal())
                .addValue("fees", b.getFees())
                .addValue("totalAmount", b.getTotalAmount())
                .addValue("idempotencyKey", b.getIdempotencyKey());

        KeyHolder kh = new GeneratedKeyHolder();
        namedJdbc.update(sql, params, kh, new String[]{"id"});
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return findById(id).orElseThrow();
    }

    public void batchInsertBookingSeats(Long bookingId, Long showtimeId, List<BookingSeat> items) {
        String sql = """
                INSERT INTO booking_seats (
                    booking_id, showtime_id, seat_id, price_at_booking, status, created_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', NOW())
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                BookingSeat item = items.get(i);
                ps.setLong(1, bookingId);
                ps.setLong(2, showtimeId);
                ps.setLong(3, item.getSeatId());
                ps.setBigDecimal(4, item.getPriceAtBooking());
            }

            @Override
            public int getBatchSize() {
                return items.size();
            }
        });
    }

    public List<BookingSeat> findBookingSeats(Long bookingId) {
        String sql = """
                SELECT bs.id, bs.booking_id, bs.showtime_id, bs.seat_id,
                       s.row_label, s.seat_number, s.seat_type,
                       bs.price_at_booking, bs.status, bs.created_at
                FROM booking_seats bs
                JOIN seats s ON s.id = bs.seat_id
                WHERE bs.booking_id = ?
                ORDER BY s.row_label ASC, s.seat_number ASC
                """;
        return jdbcTemplate.query(sql, BOOKING_SEAT_ROW_MAPPER, bookingId);
    }

    public void cancelBookingAndReleaseSeats(Long bookingId) {
        jdbcTemplate.update(
                "UPDATE bookings SET status = 'CANCELLED', cancelled_at = NOW() WHERE id = ?",
                bookingId
        );
        jdbcTemplate.update(
                "UPDATE booking_seats SET status = 'RELEASED' WHERE booking_id = ?",
                bookingId
        );
    }

    public void cancelAllBookingsForShowtime(Long showtimeId) {
        jdbcTemplate.update(
                "UPDATE bookings SET status = 'CANCELLED', cancelled_at = NOW() WHERE showtime_id = ? AND status = 'CONFIRMED'",
                showtimeId
        );
        jdbcTemplate.update(
                "UPDATE booking_seats SET status = 'RELEASED' WHERE showtime_id = ? AND status = 'ACTIVE'",
                showtimeId
        );
    }
}
