package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.HomeSection;
import com.thulirkumver.bookstore.model.OrderItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CashbackCalculatorTest {
    private final CashbackCalculator calculator = new CashbackCalculator();

    @Test
    void fixedAndPercentRulesArePerBookAndExcludeUnselectedItems() {
        HomeSection section = new HomeSection();
        section.setKey("cashback");
        section.setBookIds(List.of("book-a", "book-b"));
        section.setConfig(Map.of("perBookCashbacks", Map.of(
                "book-a", Map.of("type", "FIXED", "value", 10),
                "book-b", Map.of("type", "PERCENT", "value", 7.5),
                "book-c", Map.of("type", "FIXED", "value", 100)
        )));
        long result = calculator.calculatePaise(List.of(
                new OrderItem("book-a", "A", "", 199.99, 2),
                new OrderItem("book-b", "B", "", 149.5, 1),
                new OrderItem("book-c", "C", "", 30, 1)
        ), section);
        assertEquals(3121, result); // Rs 20.00 + Rs 11.21
        section.setActive(false);
        assertEquals(0, calculator.calculatePaise(List.of(
                new OrderItem("book-a", "A", "", 199.99, 2)), section));
    }

    @Test
    void fixedRuleCannotExceedPriceOfBook() {
        HomeSection section = new HomeSection();
        section.setKey("cashback");
        section.setBookIds(List.of("book-c"));
        section.setConfig(Map.of("perBookCashbacks", Map.of(
                "book-c", Map.of("type", "FIXED", "value", 500))));
        assertEquals(6000, calculator.calculatePaise(List.of(
                new OrderItem("book-c", "C", "", 30, 2)), section));
    }
}
