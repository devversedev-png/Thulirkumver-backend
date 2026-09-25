package com.thulirkumver.bookstore.dto;

import com.thulirkumver.bookstore.model.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RazorpayVerifyRequest(
        @NotBlank String razorpayOrderId,
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature,
        @NotNull @Valid Address address,
        String couponCode
) {
}
