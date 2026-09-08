package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.CheckoutRequest;
import com.thulirkumver.bookstore.model.*;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.repository.OfferRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OrderService {
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PACKED = "PACKED";
    public static final String SHIPPED = "SHIPPED";
    public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    private final OrderRepository orderRepository;
    private final BookRepository bookRepository;
    private final UserService userService;
    private final OfferRepository offerRepository;

    public OrderService(OrderRepository orderRepository, BookRepository bookRepository, UserService userService, OfferRepository offerRepository) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.userService = userService;
        this.offerRepository = offerRepository;
    }

    public synchronized Order placeOrder(String email, CheckoutRequest request) {
        User user = userService.getByEmail(email);
        if (user.getCart() == null || user.getCart().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        double subtotal = 0;

        for (CartItem cartItem : user.getCart()) {
            Book book = bookRepository.findById(cartItem.getBookId())
                    .orElseThrow(() -> new IllegalArgumentException("One of the books is unavailable"));

            if (!cartItem.isStockReserved()) {
                if (!book.isAvailable() || book.getStock() < cartItem.getQuantity()) {
                    throw new IllegalArgumentException(book.getTitle() + " does not have enough stock");
                }
                book.setStock(book.getStock() - cartItem.getQuantity());
                bookRepository.save(book);
                cartItem.setStockReserved(true);
            }

            orderItems.add(new OrderItem(
                    book.getId(), book.getTitle(), book.getImage(), book.getPrice(), cartItem.getQuantity()
            ));
            subtotal += book.getPrice() * cartItem.getQuantity();
        }

        double shipping = subtotal >= 499 ? 0 : 60;
        Offer appliedOffer = resolveOffer(request.couponCode(), subtotal);
        double discount = appliedOffer == null ? 0 : calculateDiscount(appliedOffer, subtotal);
        Instant now = Instant.now();

        Order order = new Order();
        order.setOrderNumber(makeOrderNumber());
        order.setUserId(user.getId());
        order.setCustomerName(user.getName());
        order.setCustomerEmail(user.getEmail());
        order.setCustomerMobile(user.getMobile());
        order.setItems(orderItems);
        order.setDeliveryAddress(request.address());
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentStatus("PENDING");
        order.setStatus(BOOKING_CONFIRMED);
        order.setStatusHistory(new ArrayList<>(List.of(
                new OrderStatusEvent(BOOKING_CONFIRMED, "Order received and booking confirmed", now)
        )));
        order.setSubtotal(round(subtotal));
        order.setShipping(shipping);
        order.setDiscount(round(discount));
        order.setOfferCode(appliedOffer == null ? null : appliedOffer.getCode());
        order.setTotal(round(Math.max(0, subtotal + shipping - discount)));
        order.setStockRestored(false);
        order.setCreatedAt(now);
        order.setStatusUpdatedAt(now);

        Order saved = orderRepository.save(order);

        user.getCart().clear();
        if (user.getAddresses().isEmpty()) {
            user.getAddresses().add(request.address());
        }
        userService.save(user);
        return saved;
    }

    public List<Order> getOrders(String email) {
        User user = userService.getByEmail(email);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public Order getOrder(String email, String orderId) {
        User user = userService.getByEmail(email);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!user.getId().equals(order.getUserId())) {
            throw new IllegalArgumentException("Order not found");
        }
        return order;
    }

    public List<Order> getAdminOrders(String status, String from, String to, String month, String q) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        YearMonth selectedMonth = parseMonth(month);
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> status == null || status.isBlank() || status.equalsIgnoreCase(order.getStatus()))
                .filter(order -> {
                    if (order.getCreatedAt() == null) return fromDate == null && toDate == null && selectedMonth == null;
                    LocalDate date = order.getCreatedAt().atZone(zone).toLocalDate();
                    if (fromDate != null && date.isBefore(fromDate)) return false;
                    if (toDate != null && date.isAfter(toDate)) return false;
                    return selectedMonth == null || YearMonth.from(date).equals(selectedMonth);
                })
                .filter(order -> search.isBlank() || searchable(order).contains(search))
                .toList();
    }

    public synchronized Order updateStatus(String orderId, String newStatus, String note) {
        String status = normalizeStatus(newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (CANCELLED.equalsIgnoreCase(order.getStatus()) && !CANCELLED.equals(status)) {
            throw new IllegalArgumentException("Cancelled order cannot be reopened");
        }

        if (CANCELLED.equals(status) && !CANCELLED.equalsIgnoreCase(order.getStatus()) && !order.isStockRestored()) {
            restoreOrderStock(order);
            order.setStockRestored(true);
        }

        Instant now = Instant.now();
        order.setStatus(status);
        order.setStatusUpdatedAt(now);
        if (DELIVERED.equals(status)) {
            order.setPaymentStatus("PAID");
        }
        if (order.getStatusHistory() == null) {
            order.setStatusHistory(new ArrayList<>());
        }
        order.getStatusHistory().add(new OrderStatusEvent(
                status,
                note == null || note.isBlank() ? defaultNote(status) : note.trim(),
                now
        ));
        return orderRepository.save(order);
    }

    private Offer resolveOffer(String code, double subtotal) {
        if (code == null || code.isBlank()) return null;
        Offer offer = offerRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid offer code"));
        String today = LocalDate.now().toString();
        if (!offer.isActive()) throw new IllegalArgumentException("This offer is not active");
        if (offer.getStartDate() != null && !offer.getStartDate().isBlank() && offer.getStartDate().compareTo(today) > 0)
            throw new IllegalArgumentException("This offer has not started yet");
        if (offer.getEndDate() != null && !offer.getEndDate().isBlank() && offer.getEndDate().compareTo(today) < 0)
            throw new IllegalArgumentException("This offer has expired");
        if (subtotal < offer.getMinOrderAmount())
            throw new IllegalArgumentException("Minimum order for this offer is ₹" + offer.getMinOrderAmount());
        return offer;
    }

    private double calculateDiscount(Offer offer, double subtotal) {
        if ("FIXED".equalsIgnoreCase(offer.getDiscountType())) {
            return Math.min(subtotal, offer.getDiscountValue());
        }
        return Math.min(subtotal, subtotal * offer.getDiscountValue() / 100.0);
    }

    private void restoreOrderStock(Order order) {
        for (OrderItem item : order.getItems()) {
            bookRepository.findById(item.getBookId()).ifPresent(book -> {
                book.setStock(book.getStock() + item.getQuantity());
                bookRepository.save(book);
            });
        }
    }

    private String searchable(Order order) {
        return String.join(" ",
                safe(order.getOrderNumber()),
                safe(order.getCustomerName()),
                safe(order.getCustomerEmail()),
                safe(order.getCustomerMobile())
        ).toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        if (value == null) throw new IllegalArgumentException("Order status is required");
        String status = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        List<String> allowed = List.of(BOOKING_CONFIRMED, CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED);
        if (!allowed.contains(status)) {
            throw new IllegalArgumentException("Invalid order status");
        }
        return status;
    }

    private String defaultNote(String status) {
        return switch (status) {
            case CONFIRMED -> "Order confirmed by the bookstore";
            case PACKED -> "Books packed and ready for dispatch";
            case SHIPPED -> "Order shipped";
            case OUT_FOR_DELIVERY -> "Order is out for delivery";
            case DELIVERED -> "Order delivered successfully";
            case CANCELLED -> "Order cancelled";
            default -> "Order booking confirmed";
        };
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }

    private YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) return null;
        return YearMonth.parse(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String makeOrderNumber() {
        String stamp = DateTimeFormatter.ofPattern("yyMMddHHmmss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return "TVB" + stamp;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
