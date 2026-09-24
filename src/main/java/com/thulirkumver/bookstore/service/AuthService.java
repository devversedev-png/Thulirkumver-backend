package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.*;
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

    public AuthResponse updateProfile(
            String authenticatedEmail,
            ProfileUpdateRequest request
    ) {
        User user = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("User account not found")
                );

        String firstName =
                request.firstName() == null
                        ? ""
                        : request.firstName().trim();

        String lastName =
                request.lastName() == null
                        ? ""
                        : request.lastName().trim();

        String email =
                request.email() == null
                        ? ""
                        : request.email().trim().toLowerCase();

        String phone =
                request.phone() == null
                        ? ""
                        : request.phone().trim();

        if (firstName.isBlank()) {
            throw new IllegalArgumentException(
                    "First name is required"
            );
        }

        if (email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required"
            );
        }

        /*
         * If the email is being changed, make sure
         * another account does not already use it.
         */
        if (!email.equalsIgnoreCase(user.getEmail())) {

            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new IllegalArgumentException(
                        "This email address is already registered"
                );
            }

            user.setEmail(email);
        }

        String fullName =
                lastName.isBlank()
                        ? firstName
                        : firstName + " " + lastName;

        user.setName(fullName);
        user.setMobile(phone);

        user = userRepository.save(user);

        return response(user);
    }

    public void changePassword(
            String authenticatedEmail,
            ChangePasswordRequest request
    ) {
        User user = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User account not found"
                        )
                );

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Current password is incorrect"
            );
        }

        if (!request.newPassword().equals(
                request.confirmPassword()
        )) {
            throw new IllegalArgumentException(
                    "New passwords do not match"
            );
        }

        if (request.newPassword().length() < 6) {
            throw new IllegalArgumentException(
                    "New password must contain at least 6 characters"
            );
        }

        /*
         * Never store the raw password.
         */
        user.setPassword(
                passwordEncoder.encode(
                        request.newPassword()
                )
        );

        userRepository.save(user);
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
                user.getRole(),
                user.getMobile()
        );
    }


}
