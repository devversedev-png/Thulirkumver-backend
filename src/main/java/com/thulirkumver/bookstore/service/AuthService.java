package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.AuthResponse;
import com.thulirkumver.bookstore.dto.LoginRequest;
import com.thulirkumver.bookstore.dto.RegisterRequest;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        Instant now = Instant.now();
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setMobile(request.mobile());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("CUSTOMER");
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        user = userRepository.save(user);

        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = findAndCheckPassword(request);
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Use the admin login page");
        }
        return response(markLogin(user));
    }

    public AuthResponse adminLogin(LoginRequest request) {
        User user = findAndCheckPassword(request);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("Admin access only");
        }
        return response(markLogin(user));
    }

    private User findAndCheckPassword(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("CUSTOMER");
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }
        return user;
    }

    private User markLogin(User user) {
        user.setLastLoginAt(Instant.now());
        return userRepository.save(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(
                jwtService.createToken(user.getEmail()),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
