package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.model.PaymentIntent;
import com.thulirkumver.bookstore.repository.PaymentIntentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Admin-only endpoint; covered by the existing /api/admin/** security rule. */
@RestController
@RequestMapping("/api/admin/payment-intents")
public class AdminPaymentReviewController {
    private final PaymentIntentRepository intents;
    public AdminPaymentReviewController(PaymentIntentRepository intents) { this.intents = intents; }
    @GetMapping("/review-required")
    public List<PaymentIntent> reviewRequired() {
        return intents.findByStatusOrderByCreatedAtDesc("REVIEW_REQUIRED");
    }
}
