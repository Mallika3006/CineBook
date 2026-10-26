package com.cinebook.payment;

import java.math.BigDecimal;

/**
 * Extensible payment integration contract.
 * Allows plugging in Razorpay, Stripe, or PayU without altering core seat-booking logic.
 */
public interface PaymentGateway {

    record PaymentExecutionResult(String provider, String providerReference, PaymentStatus status) {}

    PaymentExecutionResult authorizeDemoOrExternal(String bookingReference, BigDecimal amount);
}
