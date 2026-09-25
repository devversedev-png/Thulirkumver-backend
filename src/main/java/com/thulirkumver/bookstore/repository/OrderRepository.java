package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Order> findAllByOrderByCreatedAtDesc();
    Optional<Order> findByRazorpayPaymentId(String razorpayPaymentId);
}
