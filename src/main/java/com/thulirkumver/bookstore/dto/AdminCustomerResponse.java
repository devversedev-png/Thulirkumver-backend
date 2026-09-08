package com.thulirkumver.bookstore.dto;

import java.time.Instant;

public record AdminCustomerResponse(
        String id,
        String name,
        String email,
        String mobile,
        Instant createdAt,
        Instant lastLoginAt,
        long orderCount,
        double totalSpent,
        Instant lastOrderAt
) {
}
