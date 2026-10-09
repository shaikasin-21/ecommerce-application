package com.asin.ecommerce.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public String createCharge(Long orderId, BigDecimal amount) {
        return "mock_pay_" + UUID.randomUUID().toString().replace("-", "");
    }
}
