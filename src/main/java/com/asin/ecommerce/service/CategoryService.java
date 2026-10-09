package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.CategoryRequest;
import com.asin.ecommerce.dto.Dtos.CategoryResponse;
import com.asin.ecommerce.dto.Mappers;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.Category;
import com.asin.ecommerce.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream().map(Mappers::toCategory).toList();
    }

    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Category already exists: " + name);
        }
        return Mappers.toCategory(categoryRepository.save(new Category(name)));
    }
}
