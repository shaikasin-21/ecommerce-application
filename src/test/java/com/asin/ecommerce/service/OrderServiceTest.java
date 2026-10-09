package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.OrderResponse;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.*;
import com.asin.ecommerce.repository.CartRepository;
import com.asin.ecommerce.repository.OrderRepository;
import com.asin.ecommerce.repository.PaymentRepository;
import com.asin.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String EMAIL = "buyer@shop.com";

    @Mock UserRepository userRepository;
    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks OrderService orderService;

    private User user;
    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);

        product = new Product();
        product.setId(10L);
        product.setName("Widget");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);

        cart = new Cart();
        cart.setId(7L);
        cart.setUser(user);
    }

    private void addToCart(int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        cart.getItems().add(item);
    }

    @Test
    void placeOrder_deductsStock_clearsCart_andPublishesEvent() {
        addToCart(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.placeOrder(EMAIL);

        assertThat(product.getStock()).isEqualTo(3);
        assertThat(cart.getItems()).isEmpty();
        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.total()).isEqualByComparingTo("200.00");
        assertThat(response.items()).hasSize(1);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    void placeOrder_insufficientStock_throwsConflict_andDoesNotSave() {
        product.setStock(1);
        addToCart(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void placeOrder_emptyCart_throwsBadRequest() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.placeOrder(EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancel_restoresStock_andRefundsPaidPayment() {
        product.setStock(3);
        Order order = orderWith(OrderStatus.PAID, 2);
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);
        when(orderRepository.findByIdAndUserEmail(5L, EMAIL)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(5L)).thenReturn(List.of(payment));

        OrderResponse response = orderService.cancel(EMAIL, 5L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStock()).isEqualTo(5);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void cancel_shippedOrder_isRejected() {
        Order order = orderWith(OrderStatus.SHIPPED, 1);
        when(orderRepository.findByIdAndUserEmail(5L, EMAIL)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(EMAIL, 5L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void updateStatus_onlyAllowsForwardAdminTransitions() {
        Order order = orderWith(OrderStatus.PAID, 1);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThat(orderService.updateStatus(5L, OrderStatus.SHIPPED).status()).isEqualTo(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> orderService.updateStatus(5L, OrderStatus.PAID))
                .isInstanceOf(ApiException.class);
    }

    private Order orderWith(OrderStatus status, int quantity) {
        Order order = new Order();
        order.setId(5L);
        order.setUser(user);
        order.setStatus(status);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        order.getItems().add(item);
        order.setTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return order;
    }
}
