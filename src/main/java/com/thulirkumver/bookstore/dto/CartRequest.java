package com.thulirkumver.bookstore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CartRequest(
        @NotBlank String bookId,
        @Min(1) int quantity
) {
}
