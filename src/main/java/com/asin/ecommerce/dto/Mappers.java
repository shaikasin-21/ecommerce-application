package com.asin.ecommerce.dto;

import com.asin.ecommerce.dto.Dtos.*;
import com.asin.ecommerce.model.*;

import java.math.BigDecimal;

public final class Mappers {
    private Mappers() {}

    public static ProductResponse toProduct(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock(),
                p.getCategory().getId(), p.getCategory().getName());
    }

    public static CategoryResponse toCategory(Category c) {
        return new CategoryResponse(c.getId(), c.getName());
    }

    public static CartResponse toCart(Cart cart) {
        var items = cart.getItems().stream().map(i -> {
            BigDecimal unit = i.getProduct().getPrice();
            return new CartItemResponse(i.getId(), i.getProduct().getId(), i.getProduct().getName(), unit,
                    i.getQuantity(), unit.multiply(BigDecimal.valueOf(i.getQuantity())));
        }).toList();
        BigDecimal total = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), items, total);
    }

    public static OrderResponse toOrder(Order o) {
        var items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProduct().getId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(o.getId(), o.getStatus(), o.getTotal(), o.getCreatedAt(), items);
    }

    public static PaymentResponse toPayment(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrder().getId(), p.getStatus(), p.getAmount(),
                p.getGateway(), p.getProviderRef());
    }
}
