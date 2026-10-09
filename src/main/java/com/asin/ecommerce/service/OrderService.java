package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.OrderResponse;
import com.asin.ecommerce.dto.Dtos.PageResponse;
import com.asin.ecommerce.dto.Mappers;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.*;
import com.asin.ecommerce.repository.CartRepository;
import com.asin.ecommerce.repository.OrderRepository;
import com.asin.ecommerce.repository.PaymentRepository;
import com.asin.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Allowed admin transitions. Cancellation goes through {@link #cancel}. */
    private static final Map<OrderStatus, OrderStatus> ADMIN_NEXT = Map.of(
            OrderStatus.PAID, OrderStatus.SHIPPED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private static final Set<OrderStatus> CANCELLABLE = EnumSet.of(OrderStatus.PLACED, OrderStatus.PAID);

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Cart -> order in ONE transaction: validates stock, deducts it, snapshots prices, empties the cart.
     * Product.@Version makes concurrent buyers of the last items fail with an optimistic-lock error (HTTP 409)
     * instead of overselling.
     */
    @Transactional
    public OrderResponse placeOrder(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        Cart cart = cartRepository.findByUserId(user.getId())
                .filter(c -> !c.getItems().isEmpty())
                .orElseThrow(() -> ApiException.badRequest("Cart is empty"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PLACED);

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem ci : cart.getItems()) {
            Product product = ci.getProduct();
            if (!product.isActive()) {
                throw ApiException.conflict("'" + product.getName() + "' is no longer available");
            }
            if (product.getStock() < ci.getQuantity()) {
                throw ApiException.conflict("Insufficient stock for '" + product.getName()
                        + "'. Available: " + product.getStock());
            }
            product.setStock(product.getStock() - ci.getQuantity());

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(product);
            oi.setProductName(product.getName());
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPrice(product.getPrice());
            order.getItems().add(oi);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
        }
        order.setTotal(total);
        Order saved = orderRepository.save(order);
        cart.getItems().clear();

        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getId(), email, total));
        return Mappers.toOrder(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> myOrders(String email, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return PageResponse.from(orderRepository.findByUserEmailOrderByCreatedAtDesc(email, pageable)
                .map(Mappers::toOrder));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String email, Long id) {
        return Mappers.toOrder(findOwned(email, id));
    }

    /** Cancels an order and restores stock. Paid payments are marked REFUNDED. */
    @Transactional
    public OrderResponse cancel(String email, Long id) {
        Order order = findOwned(email, id);
        if (!CANCELLABLE.contains(order.getStatus())) {
            throw ApiException.conflict("Order cannot be cancelled in status " + order.getStatus());
        }
        for (OrderItem oi : order.getItems()) {
            Product product = oi.getProduct();
            product.setStock(product.getStock() + oi.getQuantity());
        }
        order.setStatus(OrderStatus.CANCELLED);

        for (Payment payment : paymentRepository.findByOrderId(order.getId())) {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else if (payment.getStatus() == PaymentStatus.INITIATED) {
                payment.setStatus(PaymentStatus.FAILED);
            }
        }
        return Mappers.toOrder(order);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> allOrders(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return PageResponse.from(orderRepository.findAll(pageable).map(Mappers::toOrder));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus target) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        if (ADMIN_NEXT.get(order.getStatus()) != target) {
            throw ApiException.badRequest("Invalid status change: " + order.getStatus() + " -> " + target);
        }
        order.setStatus(target);
        return Mappers.toOrder(order);
    }

    private Order findOwned(String email, Long id) {
        return orderRepository.findByIdAndUserEmail(id, email)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
    }
}
