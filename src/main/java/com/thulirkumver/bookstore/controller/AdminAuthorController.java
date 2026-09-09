package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.Author;
import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.repository.AuthorRepository;
import com.thulirkumver.bookstore.repository.BookRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin/authors")
public class AdminAuthorController {
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    public AdminAuthorController(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public List<Author> getAuthors() {
        ensureCatalogueAuthors();
        return authorRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Author::getDisplayOrder)
                        .thenComparing(Author::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @PostMapping
    public ResponseEntity<?> createAuthor(@RequestBody Author author) {
        if (author.getName() == null || author.getName().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Author name is required"));
        }
        if (authorRepository.findByNameIgnoreCase(author.getName().trim()).isPresent()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "An author with this name already exists"));
        }
        author.setId(null);
        author.setName(author.getName().trim());
        return ResponseEntity.ok(authorRepository.save(author));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAuthor(@PathVariable String id, @RequestBody Author author) {
        var existingOptional = authorRepository.findById(id);
        if (existingOptional.isEmpty()) return ResponseEntity.notFound().build();

        Author existing = existingOptional.get();
        String oldName = existing.getName();
        String newName = author.getName() == null ? "" : author.getName().trim();
        if (newName.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Author name is required"));
        }

        var duplicate = authorRepository.findByNameIgnoreCase(newName);
        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "An author with this name already exists"));
        }

        author.setId(id);
        author.setName(newName);
        Author saved = authorRepository.save(author);

        if (oldName != null && !oldName.equalsIgnoreCase(newName)) {
            List<Book> linkedBooks = bookRepository.findAll().stream()
                    .filter(book -> book.getAuthor() != null && book.getAuthor().equalsIgnoreCase(oldName))
                    .peek(book -> book.setAuthor(newName))
                    .toList();
            if (!linkedBooks.isEmpty()) bookRepository.saveAll(linkedBooks);
        }

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAuthor(@PathVariable String id) {
        if (!authorRepository.existsById(id)) return ResponseEntity.notFound().build();
        authorRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    private void ensureCatalogueAuthors() {
        int nextOrder = authorRepository.findAll().stream()
                .mapToInt(Author::getDisplayOrder)
                .max()
                .orElse(0) + 10;

        for (Book book : bookRepository.findAll()) {
            String name = book.getAuthor() == null ? "" : book.getAuthor().trim();
            if (name.isBlank() || authorRepository.findByNameIgnoreCase(name).isPresent()) continue;

            Author author = new Author();
            author.setName(name);
            author.setLanguage(book.getLanguage());
            author.setBio("");
            author.setImage("");
            author.setFeatured(false);
            author.setActive(true);
            author.setDisplayOrder(nextOrder);
            nextOrder += 10;
            authorRepository.save(author);
        }
    }

}
