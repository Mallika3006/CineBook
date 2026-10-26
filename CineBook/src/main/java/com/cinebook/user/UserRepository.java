package com.cinebook.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(UserRole.valueOf(rs.getString("role")));
        u.setActive(rs.getBoolean("is_active"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        if (createdTs != null) {
            u.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        if (updatedTs != null) {
            u.setUpdatedAt(updatedTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        return u;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, full_name, email, password_hash, role, is_active, created_at, updated_at FROM users WHERE LOWER(email) = LOWER(?)";
        List<User> list = jdbcTemplate.query(sql, USER_ROW_MAPPER, email);
        return list.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT id, full_name, email, password_hash, role, is_active, created_at, updated_at FROM users WHERE id = ?";
        List<User> list = jdbcTemplate.query(sql, USER_ROW_MAPPER, id);
        return list.stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public User save(User user) {
        String sql = "INSERT INTO users (full_name, email, password_hash, role, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail().toLowerCase());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setBoolean(5, user.isActive());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            user.setId(key.longValue());
        } else {
            Map<String, Object> keys = keyHolder.getKeys();
            if (keys != null && keys.get("id") instanceof Number n) {
                user.setId(n.longValue());
            }
        }
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    public void updateProfile(Long userId, String fullName, String passwordHash) {
        String sql = "UPDATE users SET full_name = ?, password_hash = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, fullName, passwordHash, userId);
    }
}
