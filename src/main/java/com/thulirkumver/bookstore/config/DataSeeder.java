package com.thulirkumver.bookstore.config;

import com.thulirkumver.bookstore.model.Book;
import com.thulirkumver.bookstore.model.Offer;
import com.thulirkumver.bookstore.model.Order;
import com.thulirkumver.bookstore.model.OrderStatusEvent;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.OfferRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(BookRepository bookRepository,
                      UserRepository userRepository,
                      OfferRepository offerRepository,
                      OrderRepository orderRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.admin-name}") String adminName,
                      @Value("${app.admin-email}") String adminEmail,
                      @Value("${app.admin-password}") String adminPassword) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        seedBooks();
        seedAdmin();
        seedOffer();
        migrateLegacyOrders();
    }

    private void seedBooks() {
        if (bookRepository.count() > 0) return;

        bookRepository.saveAll(List.of(
                new Book("Ponniyin Selvan", "Kalki Krishnamurthy", "Tamil Literature", "Tamil",
                        "Vanathi Pathippagam", "978000000001", "A timeless Tamil historical novel set in the Chola period.",
                        "/covers/ponniyin-selvan.svg", 450, 550, 4.8, 120, 25, true, true),
                new Book("Atomic Habits", "James Clear", "Self Help", "English",
                        "Penguin", "978000000002", "A practical guide to building good habits and breaking bad ones.",
                        "/covers/atomic-habits.svg", 399, 499, 4.7, 98, 18, true, false),
                new Book("Siru Maatrangal Periya Vazhkai", "R. Balakrishnan", "Self Help", "Tamil",
                        "Thulir Publications", "978000000003", "Simple ideas about small changes that make everyday life better.",
                        "/covers/siru-maatrangal.svg", 320, 400, 4.6, 75, 30, true, true),
                new Book("Ikigai", "Hector Garcia", "Self Help", "English",
                        "Hutchinson", "978000000004", "Ideas from Japanese culture about purpose, balance and a meaningful life.",
                        "/covers/ikigai.svg", 399, 499, 4.5, 63, 16, true, false),
                new Book("The Alchemist", "Paulo Coelho", "Fiction", "English",
                        "HarperCollins", "978000000005", "A modern classic about dreams, courage and following your path.",
                        "/covers/alchemist.svg", 399, 499, 4.6, 81, 22, true, false),
                new Book("General Knowledge 2026", "Editorial Team", "Competitive Exams", "English",
                        "Bright Learning", "978000000006", "Updated general knowledge reference for students and competitive exam learners.",
                        "/covers/general-knowledge.svg", 320, 395, 4.4, 52, 40, false, true),
                new Book("Kutti Kathaigal", "Uma Devi", "Children Books", "Tamil",
                        "Kuzhanthai Pathippagam", "978000000007", "A colourful collection of short Tamil stories for young readers.",
                        "/covers/kutti-kathaigal.svg", 250, 300, 4.7, 45, 28, false, true),
                new Book("Think Like a Monk", "Jay Shetty", "Self Help", "English",
                        "Simon & Schuster", "978000000008", "Practical reflections on calm, purpose and mental clarity.",
                        "/covers/think-like-a-monk.svg", 350, 499, 4.5, 60, 14, false, true)
        ));
    }

    private void seedAdmin() {
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        if (admin != null) {
            if (!"ADMIN".equalsIgnoreCase(admin.getRole())) {
                admin.setRole("ADMIN");
            }
            if (admin.getCreatedAt() == null) admin.setCreatedAt(Instant.now());
            userRepository.save(admin);
            return;
        }

        User user = new User();
        user.setName(adminName);
        user.setEmail(adminEmail.toLowerCase());
        user.setPassword(passwordEncoder.encode(adminPassword));
        user.setRole("ADMIN");
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
    }

    private void migrateLegacyOrders() {
        for (Order order : orderRepository.findAll()) {
            boolean changed = false;
            if (order.getStatus() == null || order.getStatus().isBlank() || "Processing".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("BOOKING_CONFIRMED");
                changed = true;
            } else {
                String normalized = order.getStatus().trim().toUpperCase().replace(' ', '_');
                if (!normalized.equals(order.getStatus())) {
                    order.setStatus(normalized);
                    changed = true;
                }
            }
            if (order.getStatusHistory() == null || order.getStatusHistory().isEmpty()) {
                Instant at = order.getCreatedAt() == null ? Instant.now() : order.getCreatedAt();
                order.setStatusHistory(new java.util.ArrayList<>(List.of(
                        new OrderStatusEvent(order.getStatus(), "Order tracking started", at)
                )));
                order.setStatusUpdatedAt(at);
                changed = true;
            }
            if ((order.getCustomerEmail() == null || order.getCustomerEmail().isBlank()) && order.getUserId() != null) {
                User customer = userRepository.findById(order.getUserId()).orElse(null);
                if (customer != null) {
                    order.setCustomerName(customer.getName());
                    order.setCustomerEmail(customer.getEmail());
                    order.setCustomerMobile(customer.getMobile());
                    changed = true;
                }
            }
            if (changed) orderRepository.save(order);
        }
    }

    private void seedOffer() {
        if (offerRepository.count() > 0) return;
        Offer offer = new Offer();
        offer.setTitle("Welcome Reader Offer");
        offer.setCode("READ10");
        offer.setDescription("A launch offer for readers shopping from Thulirkum Ver Book Store.");
        offer.setDiscountType("PERCENT");
        offer.setDiscountValue(10);
        offer.setMinOrderAmount(499);
        offer.setStartDate(LocalDate.now().minusDays(1).toString());
        offer.setEndDate(LocalDate.now().plusMonths(2).toString());
        offer.setActive(true);
        offerRepository.save(offer);
    }
}
