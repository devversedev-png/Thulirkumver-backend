package com.thulirkumver.bookstore.dto;

public record AdminOrderActionResponse(
        String action,
        String status,
        String label,
        String description,
        String tone
) {
}
