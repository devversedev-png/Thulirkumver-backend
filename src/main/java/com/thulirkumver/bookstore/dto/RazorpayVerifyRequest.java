package com.thulirkumver.bookstore.dto;

public record RazorpayVerifyRequest(
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature
) {
}