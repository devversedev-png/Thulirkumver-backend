package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.OrderItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Buy two copies of an eligible title, pay for one; each additional pair earns one free copy.
 * The free copy is a real cart unit and must be available in stock. No book price is mutated. */
public final class BogoCalculator {
    private BogoCalculator() {}

    public record Result(long discountPaise, List<OrderItem> paidItems) {}

    public static Result calculate(List<OrderItem> items, Set<String> eligibleBookIds) {
        Set<String> selected = eligibleBookIds == null ? Set.of() : new HashSet<>(eligibleBookIds);
        long discount = 0;
        List<OrderItem> paid = new ArrayList<>();
        for (OrderItem item : items) {
            if (item.getQuantity() <= 0) throw new IllegalArgumentException("Invalid quantity");
            long unitPaise = Math.round(item.getPrice() * 100.0);
            if (!Double.isFinite(item.getPrice()) || unitPaise < 0)
                throw new IllegalArgumentException("Invalid book price");
            int free = selected.contains(item.getBookId()) ? item.getQuantity() / 2 : 0;
            item.setBogoFreeQuantity(free);
            discount = Math.addExact(discount, Math.multiplyExact(unitPaise, free));
            if (item.getQuantity() > free)
                paid.add(new OrderItem(item.getBookId(), item.getTitle(), item.getImage(),
                        item.getPrice(), item.getQuantity() - free));
        }
        return new Result(discount, List.copyOf(paid));
    }
}
