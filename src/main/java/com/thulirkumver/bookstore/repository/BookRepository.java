package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.Book;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookRepository extends MongoRepository<Book, String> {
}
