package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.BookStockRequest;
import com.thulirkumver.bookstore.model.Book;
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
        if (book.getOriginalPrice() < book.getPrice()) book.setOriginalPrice(book.getPrice());
        return bookRepository.save(book);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable String id, @RequestBody Book book) {
        if (!bookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        book.setId(id);
        if (book.getOriginalPrice() < book.getPrice()) book.setOriginalPrice(book.getPrice());
        return ResponseEntity.ok(bookRepository.save(book));
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
