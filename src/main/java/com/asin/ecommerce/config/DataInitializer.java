package com.asin.ecommerce.config;

import com.asin.ecommerce.model.*;
import com.asin.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Seeds roles, a default admin and (optionally) demo catalog data on startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.seed-demo:false}") private boolean seedDemo;

    @Override
    public void run(String... args) {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.USER)));
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN)));

        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = new User();
            admin.setName("Administrator");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRoles(new HashSet<>(Set.of(adminRole, userRole)));
            userRepository.save(admin);
            log.info("Created default admin user: {}", adminEmail);
        }

        if (seedDemo && productRepository.count() == 0) {
            Category electronics = categoryRepository.save(new Category("Electronics"));
            Category books = categoryRepository.save(new Category("Books"));
            Category fashion = categoryRepository.save(new Category("Fashion"));
            seed("Wireless Mouse", "2.4GHz ergonomic wireless mouse", "799.00", 50, electronics);
            seed("Mechanical Keyboard", "Blue-switch mechanical keyboard", "2499.00", 25, electronics);
            seed("USB-C Hub", "7-in-1 USB-C hub with HDMI", "1599.00", 40, electronics);
            seed("Clean Code", "A Handbook of Agile Software Craftsmanship", "899.00", 30, books);
            seed("Effective Java", "Joshua Bloch, 3rd edition", "1099.00", 20, books);
            seed("Cotton T-Shirt", "100% cotton, regular fit", "499.00", 100, fashion);
            log.info("Seeded demo catalog");
        }
    }

    private void seed(String name, String description, String price, int stock, Category category) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setCategory(category);
        productRepository.save(p);
    }
}
