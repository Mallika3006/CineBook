package com.cinebook.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Clearly labelled Demo Checkout confirmation adapter.
 * Records DEMO_CONFIRMED status rather than fabricating external card capture.
 */
@Component
public class DemoPaymentGateway implements PaymentGateway {

    @Override
    public PaymentExecutionResult authorizeDemoOrExternal(String bookingReference, BigDecimal amount) {
        String ref = "DEMO-" + bookingReference + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentExecutionResult("DEMO_CHECKOUT", ref, PaymentStatus.DEMO_CONFIRMED);
    }
}
