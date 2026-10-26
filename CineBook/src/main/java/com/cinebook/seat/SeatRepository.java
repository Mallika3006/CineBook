package com.cinebook.seat;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class SeatRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public static final RowMapper<Seat> SEAT_ROW_MAPPER = (rs, rowNum) -> {
        Seat s = new Seat();
        s.setId(rs.getLong("id"));
        s.setScreenId(rs.getLong("screen_id"));
        s.setRowLabel(rs.getString("row_label"));
        s.setSeatNumber(rs.getInt("seat_number"));
        s.setSeatType(SeatType.valueOf(rs.getString("seat_type")));
        s.setActive(rs.getBoolean("is_active"));
        return s;
    };

    public SeatRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
    }

    public List<Seat> findByScreenId(Long screenId) {
        String sql = "SELECT id, screen_id, row_label, seat_number, seat_type, is_active FROM seats WHERE screen_id = ? ORDER BY row_label ASC, seat_number ASC";
        return jdbcTemplate.query(sql, SEAT_ROW_MAPPER, screenId);
    }

    /**
     * Locks requested seat rows in deterministic ascending ID order to prevent deadlocks
     * during concurrent seat reservation transactions.
     */
    public List<Seat> findAndLockSeatsForScreen(Long screenId, List<Long> sortedSeatIds) {
        String sql = """
                SELECT id, screen_id, row_label, seat_number, seat_type, is_active
                FROM seats
                WHERE screen_id = :screenId AND id IN (:seatIds)
                ORDER BY id ASC
                FOR UPDATE
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("screenId", screenId)
                .addValue("seatIds", sortedSeatIds);
        return namedJdbc.query(sql, params, SEAT_ROW_MAPPER);
    }

    public List<Seat> findByScreenIdAndIds(Long screenId, List<Long> seatIds) {
        String sql = """
                SELECT id, screen_id, row_label, seat_number, seat_type, is_active
                FROM seats
                WHERE screen_id = :screenId AND id IN (:seatIds)
                ORDER BY row_label ASC, seat_number ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("screenId", screenId)
                .addValue("seatIds", seatIds);
        return namedJdbc.query(sql, params, SEAT_ROW_MAPPER);
    }

    public boolean hasBookingsForScreenSeats(Long screenId) {
        String sql = """
                SELECT COUNT(*)
                FROM booking_seats bs
                JOIN seats s ON s.id = bs.seat_id
                WHERE s.screen_id = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, screenId);
        return count != null && count > 0;
    }

    public void deleteByScreenId(Long screenId) {
        jdbcTemplate.update("DELETE FROM seats WHERE screen_id = ?", screenId);
    }

    public void batchInsertSeats(Long screenId, List<Seat> seats) {
        String sql = "INSERT INTO seats (screen_id, row_label, seat_number, seat_type, is_active) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Seat s = seats.get(i);
                ps.setLong(1, screenId);
                ps.setString(2, s.getRowLabel());
                ps.setInt(3, s.getSeatNumber());
                ps.setString(4, s.getSeatType().name());
                ps.setBoolean(5, s.isActive());
            }

            @Override
            public int getBatchSize() {
                return seats.size();
            }
        });
    }
}
