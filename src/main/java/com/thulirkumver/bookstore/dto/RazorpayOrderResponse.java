package com.thulirkumver.bookstore.dto;

public record RazorpayOrderResponse(
        String orderId,
        String keyId,
        long amount,
        String currency
) {
}