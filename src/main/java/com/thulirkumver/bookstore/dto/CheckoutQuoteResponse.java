package com.thulirkumver.bookstore.dto;

public record CheckoutQuoteResponse(
        long subtotalPaise, long shippingPaise, long discountPaise,
        long totalPaise, long walletBalancePaise, long walletRedeemedPaise,
        long razorpayAmountPaise, long cashbackPaise, String currency, long bogoDiscountPaise,
        java.util.List<com.thulirkumver.bookstore.model.OrderItem> freeItems
) {}
