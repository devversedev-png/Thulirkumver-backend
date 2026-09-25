package com.thulirkumver.bookstore.dto;

import com.thulirkumver.bookstore.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RazorpayOrderRequest(
        @NotNull @Valid Address address,
        String couponCode
) {
}
