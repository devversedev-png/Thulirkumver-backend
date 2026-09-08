package com.thulirkumver.bookstore.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminOrderStatusRequest(
        @NotBlank String status,
        String note
) {
}
