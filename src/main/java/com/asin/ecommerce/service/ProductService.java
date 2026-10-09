package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.*;
import com.asin.ecommerce.dto.Mappers;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.Category;
import com.asin.ecommerce.model.Product;
import com.asin.ecommerce.repository.CategoryRepository;
import com.asin.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> SORTABLE = Set.of("id", "name", "price");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, Long categoryId, BigDecimal minPrice,
                                                BigDecimal maxPrice, int page, int size,
                                                String sortBy, String direction) {
        Specification<Product> spec = Specification.where((root, q, cb) -> cb.isTrue(root.get("active")));
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), pattern));
        }
        if (categoryId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (minPrice != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        String sortField = SORTABLE.contains(sortBy) ? sortBy : "id";
        Sort sort = "desc".equalsIgnoreCase(direction) ? Sort.by(sortField).descending() : Sort.by(sortField).ascending();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), sort);
        return PageResponse.from(productRepository.findAll(spec, pageable).map(Mappers::toProduct));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return Mappers.toProduct(findActive(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return Mappers.toProduct(productRepository.save(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = findActive(id);
        apply(p, req);
        return Mappers.toProduct(p);
    }

    /** Soft delete so historical orders keep referencing the product. */
    @Transactional
    public void delete(Long id) {
        findActive(id).setActive(false);
    }

    private Product findActive(Long id) {
        return productRepository.findById(id).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
    }

    private void apply(Product p, ProductRequest req) {
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found: " + req.categoryId()));
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setStock(req.stock());
        p.setCategory(category);
    }
}
