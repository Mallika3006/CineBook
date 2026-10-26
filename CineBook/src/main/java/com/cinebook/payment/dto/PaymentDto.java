package com.cinebook.payment.dto;

import com.cinebook.payment.Payment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentDto(
        Long id,
        Long bookingId,
        String provider,
        String providerReference,
        BigDecimal amount,
        String status,
        OffsetDateTime createdAt
) {
    public static PaymentDto from(Payment p) {
        if (p == null) {
            return null;
        }
        return new PaymentDto(
                p.getId(),
                p.getBookingId(),
                p.getProvider(),
                p.getProviderReference(),
                p.getAmount(),
                p.getStatus().name(),
                p.getCreatedAt()
        );
    }
}
