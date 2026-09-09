package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.Author;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthorRepository extends MongoRepository<Author, String> {
    Optional<Author> findByNameIgnoreCase(String name);
}
