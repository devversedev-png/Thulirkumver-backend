package com.thulirkumver.bookstore.dto;

public record AuthResponse(
        String token,
        String id,
        String name,
        String email,
        String role,
        String mobile
) {
}