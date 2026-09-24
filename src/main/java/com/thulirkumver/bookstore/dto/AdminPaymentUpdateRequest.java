package com.thulirkumver.bookstore.dto;

import jakarta.validation.constraints.DecimalMin;

public record AdminPaymentUpdateRequest(
        @DecimalMin(value = "0.0", message = "Amount paid cannot be negative")
        double amountPaid
) {
}
