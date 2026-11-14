package com.thulirkumver.bookstore.dto;

public record CheckoutQuoteRequest(String couponCode, boolean useWallet) {}
