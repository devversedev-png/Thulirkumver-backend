package com.thulirkumver.bookstore.config;

import com.thulirkumver.bookstore.model.Order;
import com.thulirkumver.bookstore.model.OrderStatusEvent;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(UserRepository userRepository,
                      OrderRepository orderRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.admin-name}") String adminName,
                      @Value("${app.admin-email}") String adminEmail,
                      @Value("${app.admin-password}") String adminPassword) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        ensureAdmin();
        migrateLegacyOrders();
    }

    private void ensureAdmin() {
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        if (admin != null) {
            if (!"ADMIN".equalsIgnoreCase(admin.getRole())) admin.setRole("ADMIN");
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
}
