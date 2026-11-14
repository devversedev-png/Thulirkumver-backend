package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.PaymentIntent;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;

public interface PaymentIntentRepository extends MongoRepository<PaymentIntent, String> {
    Optional<PaymentIntent> findByRazorpayPaymentId(String razorpayPaymentId);
    List<PaymentIntent> findByStatusOrderByCreatedAtDesc(String status);
}
