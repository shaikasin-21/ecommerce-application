package com.asin.ecommerce.controller;

import com.asin.ecommerce.dto.Dtos.PaymentInitiateRequest;
import com.asin.ecommerce.dto.Dtos.PaymentResponse;
import com.asin.ecommerce.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Payments")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse initiate(Authentication auth, @Valid @RequestBody PaymentInitiateRequest request) {
        return paymentService.initiate(auth.getName(), request.orderId());
    }

    /** Called by the payment gateway. Public, but protected by an HMAC signature over the raw body. */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
