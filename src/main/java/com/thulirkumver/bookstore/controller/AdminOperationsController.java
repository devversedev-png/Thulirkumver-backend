package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.AdminCustomerResponse;
import com.thulirkumver.bookstore.dto.AdminOrderStatusRequest;
import com.thulirkumver.bookstore.model.Offer;
import com.thulirkumver.bookstore.model.Order;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.OfferRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.repository.UserRepository;
import com.thulirkumver.bookstore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;

    public AdminOperationsController(OrderService orderService,
                                     OrderRepository orderRepository,
                                     UserRepository userRepository,
                                     OfferRepository offerRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
    }

    @GetMapping("/orders")
    public List<Order> orders(@RequestParam(required = false) String status,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              @RequestParam(required = false) String month,
                              @RequestParam(required = false) String q) {
        return orderService.getAdminOrders(status, from, to, month, q);
    }

    @PutMapping("/orders/{id}/status")
    public Order updateOrderStatus(@PathVariable String id,
                                   @Valid @RequestBody AdminOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.note());
    }

    @GetMapping("/customers")
    public List<AdminCustomerResponse> customers() {
        return userRepository.findAll().stream()
                .filter(user -> !"ADMIN".equalsIgnoreCase(user.getRole()))
                .map(this::customerResponse)
                .sorted(Comparator.comparing(AdminCustomerResponse::lastLoginAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/offers")
    public List<Offer> offers() {
        return offerRepository.findAll();
    }

    @PostMapping("/offers")
    public Offer createOffer(@RequestBody Offer offer) {
        offer.setId(null);
        return offerRepository.save(offer);
    }

    @PutMapping("/offers/{id}")
    public ResponseEntity<Offer> updateOffer(@PathVariable String id, @RequestBody Offer offer) {
        if (!offerRepository.existsById(id)) return ResponseEntity.notFound().build();
        offer.setId(id);
        return ResponseEntity.ok(offerRepository.save(offer));
    }

    @DeleteMapping("/offers/{id}")
    public ResponseEntity<Void> deleteOffer(@PathVariable String id) {
        if (!offerRepository.existsById(id)) return ResponseEntity.notFound().build();
        offerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private AdminCustomerResponse customerResponse(User user) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        double spent = orders.stream()
                .filter(order -> !OrderService.CANCELLED.equalsIgnoreCase(order.getStatus()))
                .mapToDouble(Order::getTotal)
                .sum();
        Instant lastOrder = orders.stream()
                .map(Order::getCreatedAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);

        return new AdminCustomerResponse(
                user.getId(), user.getName(), user.getEmail(), user.getMobile(),
                user.getCreatedAt(), user.getLastLoginAt(), orders.size(), spent, lastOrder
        );
    }
}
