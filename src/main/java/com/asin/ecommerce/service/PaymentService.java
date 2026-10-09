package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.PaymentResponse;
import com.asin.ecommerce.dto.Dtos.WebhookPayload;
import com.asin.ecommerce.dto.Mappers;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.Order;
import com.asin.ecommerce.model.OrderStatus;
import com.asin.ecommerce.model.Payment;
import com.asin.ecommerce.model.PaymentStatus;
import com.asin.ecommerce.repository.OrderRepository;
import com.asin.ecommerce.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.webhook-secret}")
    private String webhookSecret;

    @Transactional
    public PaymentResponse initiate(String email, Long orderId) {
        Order order = orderRepository.findByIdAndUserEmail(orderId, email)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + orderId));
        if (order.getStatus() != OrderStatus.PLACED) {
            throw ApiException.conflict("Order is not awaiting payment (status: " + order.getStatus() + ")");
        }
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setGateway(gateway.name());
        payment.setProviderRef(gateway.createCharge(order.getId(), order.getTotal()));
        payment.setStatus(PaymentStatus.INITIATED);
        return Mappers.toPayment(paymentRepository.save(payment));
    }

    /**
     * Payment-status webhook. The gateway signs the raw body with HMAC-SHA256 using the shared secret and sends the
     * hex digest in the X-Webhook-Signature header. Processing is idempotent: only INITIATED payments change.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (signature == null || !MessageDigest.isEqual(
                hmacSha256Hex(rawBody, webhookSecret).getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Malformed webhook payload");
        }
        if (payload.providerRef() == null || payload.status() == null) {
            throw ApiException.badRequest("providerRef and status are required");
        }
        Payment payment = paymentRepository.findByProviderRef(payload.providerRef())
                .orElseThrow(() -> ApiException.notFound("Unknown payment reference"));
        if (payment.getStatus() != PaymentStatus.INITIATED) {
            log.info("Ignoring duplicate webhook for {} (already {})", payment.getProviderRef(), payment.getStatus());
            return;
        }
        Order order = payment.getOrder();
        switch (payload.status()) {
            case SUCCESS -> {
                if (order.getStatus() == OrderStatus.PLACED) {
                    payment.setStatus(PaymentStatus.SUCCESS);
                    order.setStatus(OrderStatus.PAID);
                } else if (order.getStatus() == OrderStatus.CANCELLED) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                } else {
                    payment.setStatus(PaymentStatus.SUCCESS);
                }
            }
            case FAILED -> payment.setStatus(PaymentStatus.FAILED);
            default -> throw ApiException.badRequest("Unsupported webhook status: " + payload.status());
        }
    }

    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
