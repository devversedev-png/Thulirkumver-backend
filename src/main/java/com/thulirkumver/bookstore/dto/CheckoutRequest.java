package com.thulirkumver.bookstore.dto;

import com.thulirkumver.bookstore.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull @Valid Address address,
        @NotBlank String paymentMethod,
        String couponCode
) {
}
