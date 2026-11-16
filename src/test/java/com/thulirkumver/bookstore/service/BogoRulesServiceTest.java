package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.Offer;
import com.thulirkumver.bookstore.model.OrderItem;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.OfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BogoRulesServiceTest {
    private BookRepository books;
    private OfferRepository offers;
    private BogoRulesService rules;

    @BeforeEach void setup() {
        books = mock(BookRepository.class);
        offers = mock(OfferRepository.class);
        rules = new BogoRulesService(offers, books);
    }

    private static Offer bundle(String buyId, String giftId, int buyQty, int giftQty) {
        Offer o = new Offer();
        o.setId("offer1");
        o.setActive(true);
        o.setOfferType("BUY_GET");
        o.setBuyBookId(buyId);
        o.setGetBookId(giftId);
        o.setBuyQuantity(buyQty);
        o.setGetQuantity(giftQty);
        return o;
    }

    @Test void addsDifferentGiftBookAndPreservesPaidCopyForCashback() {
        var offer = bundle("A", "B", 2, 1);
        var giftBook = new Book();
        giftBook.setId("B");
        giftBook.setTitle("Gift B");
        giftBook.setPrice(199.99);
        giftBook.setStock(20);
        giftBook.setAvailable(true);
        when(offers.findAll()).thenReturn(List.of(offer));
        when(books.findById("B")).thenReturn(Optional.of(giftBook));
        var result = rules.snapshot(List.of(new OrderItem("A", "Book A", "", 300, 5)));
        assertEquals(1, result.paidItems().size());
        assertEquals(5, result.paidItems().get(0).getQuantity());
        assertEquals(1, result.giftItems().size());
        assertEquals("B", result.giftItems().get(0).getBookId());
        assertEquals(2, result.giftItems().get(0).getQuantity());
        assertEquals(2, result.giftItems().get(0).getBogoFreeQuantity());
        assertEquals(39998, result.discountPaise());
    }

    @Test void doesNotTreatGiftAsAnotherQualifyingPurchase() {
        var offer = bundle("A", "A", 1, 2);
        var book = new Book();
        book.setId("A"); book.setTitle("A"); book.setPrice(100); book.setStock(15); book.setAvailable(true);
        when(offers.findAll()).thenReturn(List.of(offer));
        when(books.findById("A")).thenReturn(Optional.of(book));
        var result = rules.snapshot(List.of(new OrderItem("A", "A", "", 100, 1)));
        assertEquals(1, result.paidItems().get(0).getQuantity());
        assertEquals(2, result.giftItems().get(0).getQuantity());
        assertEquals(20000, result.discountPaise());
    }

    @Test void preventsClaimingGiftWithInsufficientStock() {
        var offer = bundle("A", "B", 1, 2);
        var book = new Book();
        book.setId("B"); book.setTitle("B"); book.setPrice(100); book.setStock(1); book.setAvailable(true);
        when(offers.findAll()).thenReturn(List.of(offer));
        when(books.findById("B")).thenReturn(Optional.of(book));
        assertThrows(IllegalArgumentException.class,
                () -> rules.snapshot(List.of(new OrderItem("A", "A", "", 200, 1))));
    }
}
