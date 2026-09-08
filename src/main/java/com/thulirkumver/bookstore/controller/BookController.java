package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.repository.BookRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public List<Book> getBooks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice
    ) {
        return bookRepository.findAll().stream()
                .filter(book -> matchesSearch(book, q))
                .filter(book -> category == null || category.isBlank() || book.getCategory().equalsIgnoreCase(category))
                .filter(book -> language == null || language.isBlank() || book.getLanguage().equalsIgnoreCase(language))
                .filter(book -> minPrice == null || book.getPrice() >= minPrice)
                .filter(book -> maxPrice == null || book.getPrice() <= maxPrice)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBook(@PathVariable String id) {
        return bookRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean matchesSearch(Book book, String q) {
        if (q == null || q.isBlank()) return true;
        String text = q.toLowerCase(Locale.ROOT);
        return contains(book.getTitle(), text)
                || contains(book.getAuthor(), text)
                || contains(book.getCategory(), text)
                || contains(book.getPublisher(), text)
                || contains(book.getLanguage(), text);
    }

    private boolean contains(String value, String text) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(text);
    }
}
