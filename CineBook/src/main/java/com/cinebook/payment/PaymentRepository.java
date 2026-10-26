package com.cinebook.payment;

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
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public static final RowMapper<Payment> PAYMENT_ROW_MAPPER = (rs, rowNum) -> {
        Payment p = new Payment();
        p.setId(rs.getLong("id"));
        p.setBookingId(rs.getLong("booking_id"));
        p.setProvider(rs.getString("provider"));
        p.setProviderReference(rs.getString("provider_reference"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        Timestamp cTs = rs.getTimestamp("created_at");
        Timestamp uTs = rs.getTimestamp("updated_at");
        if (cTs != null) {
            p.setCreatedAt(cTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        if (uTs != null) {
            p.setUpdatedAt(uTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        return p;
    };

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Payment save(Payment payment) {
        String sql = "INSERT INTO payments (booking_id, provider, provider_reference, amount, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, payment.getBookingId());
            ps.setString(2, payment.getProvider());
            ps.setString(3, payment.getProviderReference());
            ps.setBigDecimal(4, payment.getAmount());
            ps.setString(5, payment.getStatus().name());
            return ps;
        }, kh);
        Long id = kh.getKey() != null ? kh.getKey().longValue() : ((Number) Map.copyOf(kh.getKeys()).get("id")).longValue();
        return findById(id).orElseThrow();
    }

    public Optional<Payment> findById(Long id) {
        List<Payment> list = jdbcTemplate.query(
                "SELECT id, booking_id, provider, provider_reference, amount, status, created_at, updated_at FROM payments WHERE id = ?",
                PAYMENT_ROW_MAPPER,
                id
        );
        return list.stream().findFirst();
    }

    public Optional<Payment> findByBookingId(Long bookingId) {
        List<Payment> list = jdbcTemplate.query(
                "SELECT id, booking_id, provider, provider_reference, amount, status, created_at, updated_at FROM payments WHERE booking_id = ? ORDER BY id DESC",
                PAYMENT_ROW_MAPPER,
                bookingId
        );
        return list.stream().findFirst();
    }

    public void updateStatusByBookingId(Long bookingId, PaymentStatus status) {
        jdbcTemplate.update("UPDATE payments SET status = ?, updated_at = NOW() WHERE booking_id = ?", status.name(), bookingId);
    }
}
