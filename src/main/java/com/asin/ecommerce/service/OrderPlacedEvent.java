package com.asin.ecommerce.service;

import java.math.BigDecimal;

public record OrderPlacedEvent(Long orderId, String email, BigDecimal total) {}
