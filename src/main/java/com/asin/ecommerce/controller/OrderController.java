package com.asin.ecommerce.controller;

import com.asin.ecommerce.dto.Dtos.OrderResponse;
import com.asin.ecommerce.dto.Dtos.PageResponse;
import com.asin.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orders")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(Authentication auth) {
        return orderService.placeOrder(auth.getName());
    }

    @GetMapping
    public PageResponse<OrderResponse> list(Authentication auth,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return orderService.myOrders(auth.getName(), page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(Authentication auth, @PathVariable Long id) {
        return orderService.getMyOrder(auth.getName(), id);
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication auth, @PathVariable Long id) {
        return orderService.cancel(auth.getName(), id);
    }
}
