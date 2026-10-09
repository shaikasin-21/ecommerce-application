package com.asin.ecommerce.repository;

import com.asin.ecommerce.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserEmailOrderByCreatedAtDesc(String email, Pageable pageable);
    Optional<Order> findByIdAndUserEmail(Long id, String email);
}
