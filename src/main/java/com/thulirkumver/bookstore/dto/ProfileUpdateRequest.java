package com.thulirkumver.bookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(

        @NotBlank(message = "First name is required")
        String firstName,

        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        String phone
) {
}