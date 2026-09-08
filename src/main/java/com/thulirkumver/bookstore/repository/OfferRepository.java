package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.Offer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OfferRepository extends MongoRepository<Offer, String> {
    Optional<Offer> findByCodeIgnoreCase(String code);
}
