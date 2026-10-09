package com.asin.ecommerce.service;

import java.math.BigDecimal;

/** Abstraction over a payment provider. Swap MockPaymentGateway for a Razorpay implementation later. */
public interface PaymentGateway {
    String name();

    /** Creates a charge/order at the provider and returns the provider reference id. */
    String createCharge(Long orderId, BigDecimal amount);
}
