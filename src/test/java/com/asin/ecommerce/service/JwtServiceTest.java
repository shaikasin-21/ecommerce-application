package com.asin.ecommerce.service;

import com.asin.ecommerce.security.JwtService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-characters-long";

    @Test
    void generatedTokenIsValidForSameUser() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken("a@b.com", List.of("USER"));

        assertThat(jwt.extractUsername(token)).isEqualTo("a@b.com");
        assertThat(jwt.isValid(token, "a@b.com")).isTrue();
        assertThat(jwt.isValid(token, "other@b.com")).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken("a@b.com", List.of("USER"));

        assertThat(jwt.isValid(token + "x", "a@b.com")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1_000);
        String token = jwt.generateToken("a@b.com", List.of("USER"));

        assertThat(jwt.isValid(token, "a@b.com")).isFalse();
    }
}
