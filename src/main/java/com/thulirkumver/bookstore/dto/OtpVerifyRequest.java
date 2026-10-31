package com.thulirkumver.bookstore.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank String mobile,
        @NotBlank String purpose,
        @NotBlank String otp
) {
}
