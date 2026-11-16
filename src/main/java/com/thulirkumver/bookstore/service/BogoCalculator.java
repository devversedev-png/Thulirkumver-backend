package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.OrderItem;
import java.util.List;

/** Pure Buy X Get Y quantity calculation; the free units do not trigger more gifts. */
public final class BogoCalculator {
    private BogoCalculator() {}
    public record Result(long discountPaise, List<OrderItem> paidItems, List<OrderItem> giftItems) {}

    public static int freeUnits(int purchasedQuantity, int buyQuantity, int getQuantity) {
        if (purchasedQuantity < 0 || buyQuantity < 1 || getQuantity < 1)
            throw new IllegalArgumentException("Invalid Buy X Get Y quantities");
        return Math.multiplyExact(purchasedQuantity / buyQuantity, getQuantity);
    }
}
