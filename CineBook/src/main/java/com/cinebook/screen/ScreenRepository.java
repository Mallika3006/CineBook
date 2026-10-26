package com.cinebook.screen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ScreenRepository {

    private final JdbcTemplate jdbcTemplate;

    public static final RowMapper<Screen> SCREEN_ROW_MAPPER = (rs, rowNum) -> {
        Screen s = new Screen();
        s.setId(rs.getLong("id"));
        s.setTheatreId(rs.getLong("theatre_id"));
        s.setTheatreName(rs.getString("theatre_name"));
        s.setCity(rs.getString("city"));
        s.setName(rs.getString("name"));
        s.setCapacity(rs.getInt("capacity"));
        s.setActive(rs.getBoolean("is_active"));
        return s;
    };

    private static final String BASE_SELECT = """
            SELECT sc.id, sc.theatre_id, t.name AS theatre_name, t.city,
                   sc.name, sc.capacity, sc.is_active
            FROM screens sc
            JOIN theatres t ON t.id = sc.theatre_id
            """;

    public ScreenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Screen> findByTheatreId(Long theatreId, boolean includeInactive) {
        String sql = includeInactive
                ? BASE_SELECT + " WHERE sc.theatre_id = ? ORDER BY sc.name ASC"
                : BASE_SELECT + " WHERE sc.theatre_id = ? AND sc.is_active = TRUE ORDER BY sc.name ASC";
        return jdbcTemplate.query(sql, SCREEN_ROW_MAPPER, theatreId);
    }

    public List<Screen> findAll() {
        return jdbcTemplate.query(BASE_SELECT + " ORDER BY t.city ASC, t.name ASC, sc.name ASC", SCREEN_ROW_MAPPER);
    }

    public Optional<Screen> findById(Long id) {
        List<Screen> list = jdbcTemplate.query(BASE_SELECT + " WHERE sc.id = ?", SCREEN_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    /**
     * Acquires a row-level lock on the screen record to serialize concurrent showtime scheduling.
     */
    public Optional<Screen> findByIdForUpdate(Long id) {
        String sql = """
                SELECT sc.id, sc.theatre_id, t.name AS theatre_name, t.city,
                       sc.name, sc.capacity, sc.is_active
                FROM screens sc
                JOIN theatres t ON t.id = sc.theatre_id
                WHERE sc.id = ?
                FOR UPDATE OF sc
                """;
        List<Screen> list = jdbcTemplate.query(sql, SCREEN_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Screen save(Long theatreId, String name, int capacity, boolean active) {
        String sql = "INSERT INTO screens (theatre_id, name, capacity, is_active) VALUES (?, ?, ?, ?)";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, theatreId);
            ps.setString(2, name);
            ps.setInt(3, capacity);
            ps.setBoolean(4, active);
            return ps;
        }, kh);
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return findById(id).orElseThrow();
    }

    public void updateCapacityAndStatus(Long id, String name, int capacity, boolean active) {
        jdbcTemplate.update(
                "UPDATE screens SET name = ?, capacity = ?, is_active = ? WHERE id = ?",
                name, capacity, active, id
        );
    }
}
