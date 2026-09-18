package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.HomeSection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HomeSectionRepository extends MongoRepository<HomeSection, String> {
    Optional<HomeSection> findByKeyIgnoreCase(String key);
    List<HomeSection> findAllByOrderByDisplayOrderAsc();
}
