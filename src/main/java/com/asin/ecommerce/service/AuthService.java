package com.asin.ecommerce.service;

import com.asin.ecommerce.dto.Dtos.*;
import com.asin.ecommerce.exception.ApiException;
import com.asin.ecommerce.model.Role;
import com.asin.ecommerce.model.RoleName;
import com.asin.ecommerce.model.User;
import com.asin.ecommerce.repository.RoleRepository;
import com.asin.ecommerce.repository.UserRepository;
import com.asin.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "USER role is not configured"));
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRoles(new HashSet<>(Set.of(userRole)));
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream().map(r -> r.getName().name()).sorted().toList();
        return new AuthResponse(jwtService.generateToken(user.getEmail(), roles), "Bearer", user.getEmail(), roles);
    }
}
