package com.thulirkumver.bookstore.integration.amazon;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AmazonOrderRepository extends MongoRepository<AmazonOrder, String> {
    Optional<AmazonOrder> findByAmazonOrderId(String amazonOrderId);
    List<AmazonOrder> findAllByOrderByCreatedTimeDesc();
}
