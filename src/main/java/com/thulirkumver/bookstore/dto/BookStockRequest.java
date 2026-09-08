package com.thulirkumver.bookstore.dto;

public record BookStockRequest(
        int stock,
        Boolean available
) {
}
