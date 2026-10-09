package com.asin.ecommerce.controller;

import com.asin.ecommerce.dto.Dtos.*;
import com.asin.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cart")
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(Authentication auth) {
        return cartService.getCart(auth.getName());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(Authentication auth, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(auth.getName(), request);
    }

    @PutMapping("/items/{id}")
    public CartResponse updateItem(Authentication auth, @PathVariable Long id,
                                   @Valid @RequestBody CartItemUpdateRequest request) {
        return cartService.updateItem(auth.getName(), id, request);
    }

    @DeleteMapping("/items/{id}")
    public CartResponse removeItem(Authentication auth, @PathVariable Long id) {
        return cartService.removeItem(auth.getName(), id);
    }
}
