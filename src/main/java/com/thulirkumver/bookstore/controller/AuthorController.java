package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.Author;
import com.thulirkumver.bookstore.repository.AuthorRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/authors")
public class AuthorController {
    private final AuthorRepository authorRepository;

    public AuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping
    public List<Author> getAuthors() {
        return authorRepository.findAll().stream()
                .filter(Author::isActive)
                .sorted(Comparator.comparingInt(Author::getDisplayOrder)
                        .thenComparing(Author::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
