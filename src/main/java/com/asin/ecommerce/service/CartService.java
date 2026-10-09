package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.*;
import com.asin.ecommerce.dto.Mappers;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.Cart;
import com.asin.ecommerce.model.CartItem;
import com.asin.ecommerce.model.Product;
import com.asin.ecommerce.model.User;
import com.asin.ecommerce.repository.CartItemRepository;
import com.asin.ecommerce.repository.CartRepository;
import com.asin.ecommerce.repository.ProductRepository;
import com.asin.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartResponse getCart(String email) {
        return Mappers.toCart(getOrCreateCart(email));
    }

    @Transactional
    public CartResponse addItem(String email, CartItemRequest req) {
        Cart cart = getOrCreateCart(email);
        Product product = productRepository.findById(req.productId()).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + req.productId()));

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()))
                .findFirst().orElse(null);
        int newQty = (existing == null ? 0 : existing.getQuantity()) + req.quantity();
        ensureStock(product, newQty);

        if (existing != null) {
            existing.setQuantity(newQty);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(newQty);
            cart.getItems().add(item);
            cartItemRepository.save(item);
        }
        return Mappers.toCart(cart);
    }

    @Transactional
    public CartResponse updateItem(String email, Long itemId, CartItemUpdateRequest req) {
        Cart cart = getOrCreateCart(email);
        CartItem item = cart.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Cart item not found: " + itemId));
        ensureStock(item.getProduct(), req.quantity());
        item.setQuantity(req.quantity());
        return Mappers.toCart(cart);
    }

    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        Cart cart = getOrCreateCart(email);
        boolean removed = cart.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) {
            throw ApiException.notFound("Cart item not found: " + itemId);
        }
        return Mappers.toCart(cart);
    }

    private Cart getOrCreateCart(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            return cartRepository.save(cart);
        });
    }

    private void ensureStock(Product product, int wanted) {
        if (product.getStock() < wanted) {
            throw ApiException.conflict("Only " + product.getStock() + " unit(s) of '" + product.getName() + "' in stock");
        }
    }
}
