package com.asin.ecommerce.dto;

import com.asin.ecommerce.model.OrderStatus;
import com.asin.ecommerce.model.PaymentStatus;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** All request/response DTOs as records, grouped by module. */
public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, String tokenType, String email, List<String> roles) {}

    // ---- catalog ----
    public record CategoryRequest(@NotBlank @Size(max = 100) String name) {}

    public record CategoryResponse(Long id, String name) {}

    public record ProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stock,
            @NotNull Long categoryId) {}

    public record ProductResponse(Long id, String name, String description, BigDecimal price,
                                  int stock, Long categoryId, String categoryName) {}

    // ---- cart ----
    public record CartItemRequest(@NotNull Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {}

    public record CartItemUpdateRequest(@NotNull @Min(1) @Max(100) Integer quantity) {}

    public record CartItemResponse(Long id, Long productId, String productName, BigDecimal unitPrice,
                                   int quantity, BigDecimal lineTotal) {}

    public record CartResponse(Long id, List<CartItemResponse> items, BigDecimal total) {}

    // ---- orders ----
    public record OrderItemResponse(Long productId, String productName, int quantity, BigDecimal unitPrice) {}

    public record OrderResponse(Long id, OrderStatus status, BigDecimal total, Instant createdAt,
                                List<OrderItemResponse> items) {}

    public record OrderStatusRequest(@NotNull OrderStatus status) {}

    // ---- payments ----
    public record PaymentInitiateRequest(@NotNull Long orderId) {}

    public record PaymentResponse(Long paymentId, Long orderId, PaymentStatus status, BigDecimal amount,
                                  String gateway, String providerRef) {}

    public record WebhookPayload(@NotBlank String providerRef, @NotNull PaymentStatus status) {}

    // ---- common ----
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String message,
                                Map<String, String> fieldErrors) {}
}
