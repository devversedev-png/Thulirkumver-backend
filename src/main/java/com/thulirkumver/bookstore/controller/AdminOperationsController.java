package com.thulirkumver.bookstore.controller;

import com.thulirkumver.bookstore.dto.AdminCustomerResponse;
import com.thulirkumver.bookstore.dto.AdminOrderActionResponse;
import com.thulirkumver.bookstore.dto.AdminOrderStatusRequest;
import com.thulirkumver.bookstore.dto.AdminPaymentUpdateRequest;
import com.thulirkumver.bookstore.model.Offer;
import com.thulirkumver.bookstore.repository.BookRepository;
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
    private final BookRepository bookRepository;

    public AdminOperationsController(OrderService orderService,
                                     OrderRepository orderRepository,
                                     UserRepository userRepository,
                                     OfferRepository offerRepository, BookRepository bookRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.offerRepository = offerRepository;
        this.bookRepository = bookRepository;
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
        return orderService.updateStatus(id, request.status(), request.note(), request.courierName(), request.trackingNumber());
    }

    @GetMapping("/orders/{id}/actions")
    public List<AdminOrderActionResponse> orderActions(@PathVariable String id) {
        return orderService.getAvailableActions(id);
    }

    @PutMapping("/orders/{id}/payment")
    public Order updateOrderPayment(@PathVariable String id,
                                    @Valid @RequestBody AdminPaymentUpdateRequest request) {
        return orderService.updatePayment(id, request.amountPaid());
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
        validateOffer(offer, null);
        return offerRepository.save(offer);
    }

    @PutMapping("/offers/{id}")
    public ResponseEntity<Offer> updateOffer(@PathVariable String id, @RequestBody Offer offer) {
        if (!offerRepository.existsById(id)) return ResponseEntity.notFound().build();
        offer.setId(id);
        validateOffer(offer, id);
        return ResponseEntity.ok(offerRepository.save(offer));
    }

    @DeleteMapping("/offers/{id}")
    public ResponseEntity<Void> deleteOffer(@PathVariable String id) {
        if (!offerRepository.existsById(id)) return ResponseEntity.notFound().build();
        offerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void validateOffer(Offer offer, String editingId) {
        if (offer.getTitle() == null || offer.getTitle().isBlank())
            throw new IllegalArgumentException("Offer title is required");
        if (offer.getStartDate() != null && !offer.getStartDate().isBlank()
                && offer.getEndDate() != null && !offer.getEndDate().isBlank()
                && offer.getStartDate().compareTo(offer.getEndDate()) > 0)
            throw new IllegalArgumentException("End date must not precede start date");
        if ("BUY_GET".equalsIgnoreCase(offer.getOfferType())) {
            offer.setOfferType("BUY_GET");
            offer.setCode(""); // Buy X Get Y promotions apply without a coupon.
            java.util.List<com.thulirkumver.bookstore.model.OfferBookQuantity> required = offer.getBuyItems();
            java.util.List<com.thulirkumver.bookstore.model.OfferBookQuantity> gifts = offer.getGiftItems();
            if (required.isEmpty() || gifts.isEmpty() || required.size() > 15 || gifts.size() > 15)
                throw new IllegalArgumentException("Select 1–15 qualifying and 1–15 free books");
            java.util.Set<String> requiredIds = new java.util.HashSet<>();
            java.util.Set<String> giftIds = new java.util.HashSet<>();
            for (var item : required) {
                if (item == null || item.getBookId() == null || item.getBookId().isBlank()
                        || item.getQuantity() < 1 || item.getQuantity() > 50
                        || !bookRepository.existsById(item.getBookId()) || !requiredIds.add(item.getBookId()))
                    throw new IllegalArgumentException("Select unique qualifying books with quantities from 1 to 50");
            }
            for (var item : gifts) {
                if (item == null || item.getBookId() == null || item.getBookId().isBlank()
                        || item.getQuantity() < 1 || item.getQuantity() > 50
                        || !bookRepository.existsById(item.getBookId()) || !giftIds.add(item.getBookId()))
                    throw new IllegalArgumentException("Select unique free books with quantities from 1 to 50");
            }
            // An active qualifying title can participate in only one active bundle.
            // Prevents hidden stacking and admin-dependent rule selection.
            if (offer.isActive()) {
                boolean conflict = offerRepository.findAll().stream()
                        .anyMatch(saved -> !java.util.Objects.equals(saved.getId(), editingId)
                                && saved.isActive() && "BUY_GET".equalsIgnoreCase(saved.getOfferType())
                                && saved.getBuyItems().stream().anyMatch(item -> requiredIds.contains(item.getBookId())));
                if (conflict)
                    throw new IllegalArgumentException("A qualifying book is already used by another active free-book offer");
            }
        } else {
            offer.setOfferType("COUPON");
            if (!Double.isFinite(offer.getDiscountValue()) || offer.getDiscountValue() < 0
                    || !Double.isFinite(offer.getMinOrderAmount()) || offer.getMinOrderAmount() < 0
                    || ("PERCENT".equalsIgnoreCase(offer.getDiscountType()) && offer.getDiscountValue() > 100))
                throw new IllegalArgumentException("Invalid coupon discount");
        }
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
