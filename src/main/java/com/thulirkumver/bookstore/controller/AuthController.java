package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.AuthResponse;
import com.thulirkumver.bookstore.dto.LoginRequest;
import com.thulirkumver.bookstore.dto.OtpSendRequest;
import com.thulirkumver.bookstore.dto.OtpVerifyRequest;
import com.thulirkumver.bookstore.dto.RegisterRequest;
import com.thulirkumver.bookstore.integration.msg91.OtpService;
import com.thulirkumver.bookstore.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.thulirkumver.bookstore.dto.ChangePasswordRequest;
import com.thulirkumver.bookstore.dto.ProfileUpdateRequest;

import org.springframework.security.core.Authentication;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    /** Lets the frontend decide whether to show the OTP steps. */
    @GetMapping("/otp/config")
    public Map<String, Object> otpConfig() {
        return Map.of(
                "enabled", otpService.isEnabled(),
                "requiredForRegistration", otpService.isRequiredForRegistration()
        );
    }

    @PostMapping("/otp/send")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        try {
            if ("LOGIN".equalsIgnoreCase(request.purpose())) {
                authService.sendLoginOtp(request.mobile());
            } else if ("REGISTER".equalsIgnoreCase(request.purpose())) {
                authService.sendRegisterOtp(request.mobile());
            } else {
                throw new IllegalArgumentException("Unknown OTP purpose");
            }
            return ResponseEntity.ok(Map.of("message", "OTP sent"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * REGISTER: returns {"verified":true,"otpToken":"..."} to send with /register.
     * LOGIN: returns the normal AuthResponse (token, id, name, ...).
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        try {
            if ("LOGIN".equalsIgnoreCase(request.purpose())) {
                return ResponseEntity.ok(authService.loginWithOtp(request.mobile(), request.otp()));
            }
            if ("REGISTER".equalsIgnoreCase(request.purpose())) {
                String token = authService.verifyRegisterOtp(request.mobile(), request.otp());
                return ResponseEntity.ok(Map.of("verified", true, "otpToken", token));
            }
            throw new IllegalArgumentException("Unknown OTP purpose");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> adminLogin(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.adminLogin(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        try {

            String email =
                    authentication.getName();

            AuthResponse response =
                    authService.updateProfile(
                            email,
                            request
                    );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "message",
                                    ex.getMessage()
                            )
                    );
        }
    }
}
