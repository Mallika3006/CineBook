package com.cinebook.theatre;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TheatreRepository {

    private final JdbcTemplate jdbcTemplate;

    public static final RowMapper<Theatre> THEATRE_ROW_MAPPER = (rs, rowNum) -> {
        Theatre t = new Theatre();
        t.setId(rs.getLong("id"));
        t.setName(rs.getString("name"));
        t.setAddress(rs.getString("address"));
        t.setCity(rs.getString("city"));
        t.setActive(rs.getBoolean("is_active"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            t.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }
        return t;
    };

    public TheatreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Theatre> findAll(String city, boolean includeInactive) {
        if (city != null && !city.isBlank()) {
            String sql = includeInactive
                    ? "SELECT id, name, address, city, is_active, created_at FROM theatres WHERE LOWER(city) = LOWER(?) ORDER BY name ASC"
                    : "SELECT id, name, address, city, is_active, created_at FROM theatres WHERE LOWER(city) = LOWER(?) AND is_active = TRUE ORDER BY name ASC";
            return jdbcTemplate.query(sql, THEATRE_ROW_MAPPER, city.trim());
        }
        String sql = includeInactive
                ? "SELECT id, name, address, city, is_active, created_at FROM theatres ORDER BY city ASC, name ASC"
                : "SELECT id, name, address, city, is_active, created_at FROM theatres WHERE is_active = TRUE ORDER BY city ASC, name ASC";
        return jdbcTemplate.query(sql, THEATRE_ROW_MAPPER);
    }

    public Optional<Theatre> findById(Long id) {
        List<Theatre> list = jdbcTemplate.query(
                "SELECT id, name, address, city, is_active, created_at FROM theatres WHERE id = ?",
                THEATRE_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Theatre save(Theatre t) {
        String sql = "INSERT INTO theatres (name, address, city, is_active, created_at) VALUES (?, ?, ?, ?, NOW())";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, t.getName());
            ps.setString(2, t.getAddress());
            ps.setString(3, t.getCity());
            ps.setBoolean(4, t.isActive());
            return ps;
        }, kh);
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return findById(id).orElseThrow();
    }

    public Theatre update(Long id, Theatre t) {
        String sql = "UPDATE theatres SET name = ?, address = ?, city = ?, is_active = ? WHERE id = ?";
        jdbcTemplate.update(sql, t.getName(), t.getAddress(), t.getCity(), t.isActive(), id);
        return findById(id).orElseThrow();
    }
}
