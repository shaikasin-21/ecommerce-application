package com.asin.ecommerce.service;

import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.Order;
import com.asin.ecommerce.model.OrderStatus;
import com.asin.ecommerce.model.Payment;
import com.asin.ecommerce.model.PaymentStatus;
import com.asin.ecommerce.repository.OrderRepository;
import com.asin.ecommerce.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String SECRET = "whsec_unit_test";

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentGateway gateway;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks PaymentService paymentService;

    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", SECRET);
        order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PLACED);
        payment = new Payment();
        payment.setOrder(order);
        payment.setProviderRef("ref_1");
        payment.setStatus(PaymentStatus.INITIATED);
    }

    private String body(String status) {
        return "{\"providerRef\":\"ref_1\",\"status\":\"" + status + "\"}";
    }

    @Test
    void invalidSignatureIsRejected() {
        assertThatThrownBy(() -> paymentService.handleWebhook(body("SUCCESS"), "deadbeef"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void successWebhookMarksOrderPaid() {
        String raw = body("SUCCESS");
        when(paymentRepository.findByProviderRef("ref_1")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(raw, PaymentService.hmacSha256Hex(raw, SECRET));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void failedWebhookKeepsOrderPlaced() {
        String raw = body("FAILED");
        when(paymentRepository.findByProviderRef("ref_1")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(raw, PaymentService.hmacSha256Hex(raw, SECRET));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void duplicateWebhookIsIgnored() {
        payment.setStatus(PaymentStatus.SUCCESS);
        order.setStatus(OrderStatus.SHIPPED);
        String raw = body("SUCCESS");
        when(paymentRepository.findByProviderRef("ref_1")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(raw, PaymentService.hmacSha256Hex(raw, SECRET));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}
