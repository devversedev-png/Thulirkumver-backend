package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.AdminOrderActionResponse;
import com.thulirkumver.bookstore.dto.CheckoutRequest;
import com.thulirkumver.bookstore.model.*;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.OfferRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        if (user.getAddresses().isEmpty()) user.getAddresses().add(request.address());
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
        if (!user.getId().equals(order.getUserId())) throw new IllegalArgumentException("Order not found");
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

    public List<AdminOrderActionResponse> getAvailableActions(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        String current = normalizeStatus(order.getStatus());
        List<AdminOrderActionResponse> actions = new ArrayList<>();

        if (CANCELLED.equals(current)) {
            actions.add(new AdminOrderActionResponse(
                    "REOPEN", BOOKING_CONFIRMED, "Reopen order",
                    "Reserve stock again and return the order to booking confirmed.", "warning"
            ));
            return actions;
        }

        String next = nextStatus(current);
        if (next != null) {
            actions.add(new AdminOrderActionResponse(
                    "ADVANCE", next, "Move to " + statusLabel(next),
                    "Continue this order to the next fulfilment stage.", "primary"
            ));
        }

        if (List.of(BOOKING_CONFIRMED, CONFIRMED, PACKED).contains(current)) {
            actions.add(new AdminOrderActionResponse(
                    "CANCEL", CANCELLED, "Cancel order",
                    "Close the order and return its reserved stock to inventory.", "danger"
            ));
        }

        return actions;
    }

    public synchronized Order updateStatus(String orderId, String newStatus, String note) {
        String status = normalizeStatus(newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        String current = normalizeStatus(order.getStatus());

        if (current.equals(status)) return order;

        if (CANCELLED.equals(current)) {
            if (!BOOKING_CONFIRMED.equals(status)) {
                throw new IllegalArgumentException("Cancelled orders can only be reopened to Booking Confirmed");
            }
            reserveOrderStock(order);
            order.setStockRestored(false);
            order.setPaymentStatus("PENDING");
        } else if (!isAllowedTransition(current, status)) {
            throw new IllegalArgumentException("This status change is not allowed from " + statusLabel(current));
        }

        if (CANCELLED.equals(status) && !order.isStockRestored()) {
            restoreOrderStock(order);
            order.setStockRestored(true);
        }

        Instant now = Instant.now();
        order.setStatus(status);
        order.setStatusUpdatedAt(now);
        if (DELIVERED.equals(status)) order.setPaymentStatus("PAID");

        if (order.getStatusHistory() == null) order.setStatusHistory(new ArrayList<>());
        order.getStatusHistory().add(new OrderStatusEvent(
                status,
                note == null || note.isBlank() ? defaultNote(status, CANCELLED.equals(current)) : note.trim(),
                now
        ));
        return orderRepository.save(order);
    }

    private boolean isAllowedTransition(String current, String target) {
        if (BOOKING_CONFIRMED.equals(current)) return CONFIRMED.equals(target) || CANCELLED.equals(target);
        if (CONFIRMED.equals(current)) return PACKED.equals(target) || CANCELLED.equals(target);
        if (PACKED.equals(current)) return SHIPPED.equals(target) || CANCELLED.equals(target);
        if (SHIPPED.equals(current)) return OUT_FOR_DELIVERY.equals(target);
        if (OUT_FOR_DELIVERY.equals(current)) return DELIVERED.equals(target);
        return false;
    }

    private String nextStatus(String current) {
        Map<String, String> flow = new LinkedHashMap<>();
        flow.put(BOOKING_CONFIRMED, CONFIRMED);
        flow.put(CONFIRMED, PACKED);
        flow.put(PACKED, SHIPPED);
        flow.put(SHIPPED, OUT_FOR_DELIVERY);
        flow.put(OUT_FOR_DELIVERY, DELIVERED);
        return flow.get(current);
    }

    private void reserveOrderStock(Order order) {
        List<Book> books = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Book book = bookRepository.findById(item.getBookId())
                    .orElseThrow(() -> new IllegalArgumentException(item.getTitle() + " is no longer in the catalogue"));
            if (!book.isAvailable() || book.getStock() < item.getQuantity()) {
                throw new IllegalArgumentException("Cannot reopen order. Not enough stock for " + item.getTitle());
            }
            books.add(book);
        }

        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItem item = order.getItems().get(i);
            Book book = books.get(i);
            book.setStock(book.getStock() - item.getQuantity());
            bookRepository.save(book);
        }
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
        if ("FIXED".equalsIgnoreCase(offer.getDiscountType())) return Math.min(subtotal, offer.getDiscountValue());
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
        return String.join(" ", safe(order.getOrderNumber()), safe(order.getCustomerName()), safe(order.getCustomerEmail()), safe(order.getCustomerMobile()))
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String value) {
        if (value == null) throw new IllegalArgumentException("Order status is required");
        String status = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        List<String> allowed = List.of(BOOKING_CONFIRMED, CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED);
        if (!allowed.contains(status)) throw new IllegalArgumentException("Invalid order status");
        return status;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case BOOKING_CONFIRMED -> "Booking Confirmed";
            case CONFIRMED -> "Confirmed";
            case PACKED -> "Packed";
            case SHIPPED -> "Shipped";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            default -> status;
        };
    }

    private String defaultNote(String status, boolean reopened) {
        if (reopened && BOOKING_CONFIRMED.equals(status)) return "Order reopened by admin and stock reserved again";
        return switch (status) {
            case CONFIRMED -> "Order confirmed by the bookstore";
            case PACKED -> "Books packed and ready for dispatch";
            case SHIPPED -> "Order shipped";
            case OUT_FOR_DELIVERY -> "Order is out for delivery";
            case DELIVERED -> "Order delivered successfully";
            case CANCELLED -> "Order cancelled and reserved stock returned to inventory";
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

    private String safe(String value) { return value == null ? "" : value; }

    private String makeOrderNumber() {
        String stamp = DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        return "TVB" + stamp;
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
