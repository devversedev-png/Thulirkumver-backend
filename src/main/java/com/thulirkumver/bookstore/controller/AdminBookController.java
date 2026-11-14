package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.BookStockRequest;
import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.BookPricing;
import com.thulirkumver.bookstore.repository.BookRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/books")
public class AdminBookController {
    private final BookRepository bookRepository;

    public AdminBookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public List<Book> getBooks() {
        return bookRepository.findAll();
    }

    @PostMapping
    public Book createBook(@RequestBody Book book) {
        book.setId(null);
        // Promotions are controlled only from Home Sections; a new book starts without one.
        book.setDiscountType(null);
        book.setDiscountValue(null);
        double normal = requestedNormalPrice(book);
        book.changeNormalPrice(normal);
        if (book.getOriginalPrice() < normal) book.setOriginalPrice(normal);
        return bookRepository.save(book);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable String id, @RequestBody Book book) {
        Book existing = bookRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        double normal = requestedNormalPrice(book);
        // A client that only knows the customer price may send the discounted amount back unchanged.
        // That is not a new normal price: keep the stored one so the discount is never applied twice.
        if (!book.hasStoredBasePrice() && existing.hasActiveDiscount()
                && BookPricing.close(book.getPrice(), existing.getPrice())) {
            normal = existing.normalPrice();
        }
        book.setId(id);
        // The promotion belongs to Home Sections, not to the book form.
        book.setDiscountType(existing.getDiscountType());
        book.setDiscountValue(existing.getDiscountValue());
        book.changeNormalPrice(normal);
        if (book.getOriginalPrice() < normal) book.setOriginalPrice(normal);
        return ResponseEntity.ok(bookRepository.save(book));
    }

    /** The normal selling price sent by the admin form: basePrice when present, else price. */
    private double requestedNormalPrice(Book book) {
        return book.hasStoredBasePrice() ? book.getBasePrice() : book.getPrice();
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<Book> updateStock(@PathVariable String id, @RequestBody BookStockRequest request) {
        return bookRepository.findById(id)
                .map(book -> {
                    book.setStock(request.stock());
                    if (request.available() != null) book.setAvailable(request.available());
                    return ResponseEntity.ok(bookRepository.save(book));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable String id) {
        if (!bookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bookRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
