package com.thulirkumver.bookstore.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
        @NotBlank String mobile,
        @NotBlank String purpose
) {
}
