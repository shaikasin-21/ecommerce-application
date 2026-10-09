package com.asin.ecommerce.repository;

import com.asin.ecommerce.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderRef(String providerRef);
    List<Payment> findByOrderId(Long orderId);
}
