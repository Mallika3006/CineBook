package com.cinebook.genre;

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
public class GenreRepository {

    private final JdbcTemplate jdbcTemplate;

    public static final RowMapper<Genre> GENRE_ROW_MAPPER = (rs, rowNum) ->
            new Genre(rs.getLong("id"), rs.getString("name"));

    public GenreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Genre> findAll() {
        return jdbcTemplate.query("SELECT id, name FROM genres ORDER BY name ASC", GENRE_ROW_MAPPER);
    }

    public Optional<Genre> findById(Long id) {
        List<Genre> list = jdbcTemplate.query("SELECT id, name FROM genres WHERE id = ?", GENRE_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public Optional<Genre> findByName(String name) {
        List<Genre> list = jdbcTemplate.query("SELECT id, name FROM genres WHERE LOWER(name) = LOWER(?)", GENRE_ROW_MAPPER, name);
        return list.stream().findFirst();
    }

    public Genre save(String name) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO genres (name) VALUES (?)", new String[]{"id"});
            ps.setString(1, name);
            return ps;
        }, kh);
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return new Genre(id, name);
    }
}
