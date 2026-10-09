package com.asin.ecommerce.controller;

import com.asin.ecommerce.dto.Dtos.*;
import com.asin.ecommerce.service.CategoryService;
import com.asin.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Products (public)")
@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/products")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(name = "category", required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return productService.search(search, categoryId, minPrice, maxPrice, page, size, sortBy, direction);
    }

    @GetMapping("/products/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categoryService.list();
    }
}
