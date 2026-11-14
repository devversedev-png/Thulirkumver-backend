package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.OrderItem;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BogoCalculatorTest {
    private static OrderItem item(String bookId, double price, int qty) {
        return new OrderItem(bookId, "Book", "cover", price, qty);
    }

    @Test void oneCopyHasNoFreeUnit() {
        List<OrderItem> items = new ArrayList<>(List.of(item("A", 199.99, 1)));
        var quote = BogoCalculator.calculate(items, Set.of("A"));
        assertEquals(0, quote.discountPaise());
        assertEquals(0, items.get(0).getBogoFreeQuantity());
        assertEquals(1, quote.paidItems().get(0).getQuantity());
    }

    @Test void twoThreeAndFourCopiesUsePairs() {
        for (int quantity = 2; quantity <= 4; quantity++) {
            List<OrderItem> items = new ArrayList<>(List.of(item("A", 250, quantity)));
            var quote = BogoCalculator.calculate(items, Set.of("A"));
            assertEquals(quantity / 2 * 25000L, quote.discountPaise());
            assertEquals(quantity / 2, items.get(0).getBogoFreeQuantity());
            assertEquals(quantity - quantity / 2, quote.paidItems().get(0).getQuantity());
            assertEquals(quantity, items.get(0).getQuantity()); // all copies still ship
        }
    }

    @Test void unselectedBooksNeverDiscount() {
        List<OrderItem> items = new ArrayList<>(List.of(item("A", 400, 2), item("B", 350, 3)));
        var quote = BogoCalculator.calculate(items, Set.of("B"));
        assertEquals(35000, quote.discountPaise());
        assertEquals(0, items.get(0).getBogoFreeQuantity());
        assertEquals(1, items.get(1).getBogoFreeQuantity());
    }

    @Test void validatesQuantityAndPaisePrecision() {
        assertEquals(19999, BogoCalculator.calculate(new ArrayList<>(List.of(item("A",199.99,2))),Set.of("A")).discountPaise());
        assertThrows(IllegalArgumentException.class, () -> BogoCalculator.calculate(List.of(item("A", 200, 0)), Set.of("A")));
    }
}
