package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.dto.AdminOrderActionResponse;
import com.thulirkumver.bookstore.dto.CheckoutRequest;
import com.thulirkumver.bookstore.model.*;
import com.thulirkumver.bookstore.repository.BookRepository;
import com.thulirkumver.bookstore.repository.OfferRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CashbackRulesService cashbackRules;
    private final WalletService walletService;
    private final BogoRulesService bogoRules;

    public OrderService(OrderRepository orderRepository, BookRepository bookRepository, UserService userService,
                        OfferRepository offerRepository, CashbackRulesService cashbackRules, WalletService walletService,
                        BogoRulesService bogoRules) {
        this.orderRepository = orderRepository;
        this.bookRepository = bookRepository;
        this.userService = userService;
        this.offerRepository = offerRepository;
        this.cashbackRules = cashbackRules;
        this.walletService = walletService;
        this.bogoRules = bogoRules;
    }

    public synchronized double calculateCheckoutTotal(String email, com.thulirkumver.bookstore.model.Address address, String couponCode) {
        User user = userService.getByEmail(email);
        if (user.getCart() == null || user.getCart().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        List<OrderItem> items = new ArrayList<>();
        double subtotal = 0;
        for (CartItem cartItem : user.getCart()) {
            Book book = bookRepository.findById(cartItem.getBookId())
                    .orElseThrow(() -> new IllegalArgumentException("One of the books is unavailable"));
            if (cartItem.getQuantity() <= 0 || (!cartItem.isStockReserved()
                    && (!book.isAvailable() || book.getStock() < cartItem.getQuantity()))) {
                throw new IllegalArgumentException(book.getTitle() + " does not have enough stock");
            }
            items.add(new OrderItem(book.getId(), book.getTitle(), book.getImage(), book.getPrice(), cartItem.getQuantity()));
            subtotal += book.getPrice() * cartItem.getQuantity();
        }
        long bogoPaise = bogoRules.snapshot(items).discountPaise();
        double merchandiseDue = round(Math.max(0, subtotal - bogoPaise / 100.0));
        double shipping = merchandiseDue >= 499 ? 0 : 60;
        Offer appliedOffer = resolveOffer(couponCode, merchandiseDue);
        double couponDiscount = appliedOffer == null ? 0 : calculateDiscount(appliedOffer, merchandiseDue);
        return round(Math.max(0, merchandiseDue + shipping - couponDiscount));
    }

    /** An immutable snapshot of order prices, eligible wallet redemption and cashback. */
    public PaymentIntent prepareOnlineCheckout(String email, Address address, String couponCode) {
        return prepareOnlineCheckout(email, address, couponCode, false);
    }

    public PaymentIntent prepareOnlineCheckout(String email, Address address, String couponCode, boolean useWallet) {
        User user = userService.getByEmail(email);
        if (user.getCart() == null || user.getCart().isEmpty())
            throw new IllegalArgumentException("Cart is empty");
        if (address == null) throw new IllegalArgumentException("Delivery address is required");

        List<OrderItem> items = new ArrayList<>();
        double subtotal = 0;
        for (CartItem entry : user.getCart()) {
            if (entry.getQuantity() <= 0) throw new IllegalArgumentException("Invalid cart quantity");
            Book book = bookRepository.findById(entry.getBookId())
                    .orElseThrow(() -> new IllegalArgumentException("One of the books is unavailable"));
            if (!entry.isStockReserved() && (!book.isAvailable() || book.getStock() < entry.getQuantity()))
                throw new IllegalArgumentException(book.getTitle() + " does not have enough stock");
            items.add(new OrderItem(book.getId(), book.getTitle(), book.getImage(), book.getPrice(), entry.getQuantity()));
            subtotal += book.getPrice() * entry.getQuantity();
        }
        BogoCalculator.Result bogo = bogoRules.snapshot(items);
        double merchandiseDue = round(Math.max(0, subtotal - bogo.discountPaise() / 100.0));
        double shipping = merchandiseDue >= 499 ? 0 : 60;
        Offer offer = resolveOffer(couponCode, merchandiseDue);
        double couponDiscount = offer == null ? 0 : calculateDiscount(offer, merchandiseDue);
        long grossPaise = Math.round(round(Math.max(0, merchandiseDue + shipping - couponDiscount)) * 100);
        if (grossPaise <= 0) throw new IllegalArgumentException("Invalid checkout amount");
        long available = Math.max(0, walletService.balancePaise(user.getId()));
        WalletAmounts amounts = WalletAmounts.calculate(grossPaise, available,
                useWallet, cashbackRules.snapshotCashbackPaise(bogo.paidItems()));
        long redeemedPaise = amounts.redeemedPaise();
        long amountPaise = amounts.razorpayPaise();
        PaymentIntent intent = new PaymentIntent();
        intent.setUserId(user.getId());
        intent.setItems(items);
        intent.setAddress(address);
        intent.setCouponCode(couponCode);
        intent.setOfferCode(offer == null ? null : offer.getCode());
        intent.setSubtotalPaise(Math.round(subtotal * 100));
        intent.setBogoDiscountPaise(bogo.discountPaise());
        intent.setShippingPaise(Math.round(shipping * 100));
        // Make the stored components add up to the exact charged amount.
        intent.setDiscountPaise(intent.getSubtotalPaise() + intent.getShippingPaise() - grossPaise);
        intent.setAmountPaise(amountPaise);
        intent.setWalletRedeemedPaise(redeemedPaise);
        // Promotional cashback is earned only on the Razorpay-funded portion:
        // a wallet-only purchase never produces self-replenishing cashback.
        intent.setCashbackPaise(amounts.cashbackPaise());
        return intent;
    }

    /** Called only from a MongoDB transaction after Razorpay confirms captured payment. */
    public Order placeCapturedOrder(String email, PaymentIntent intent, String razorpayPaymentId) {
        User user = userService.getByEmail(email);
        if (!user.getId().equals(intent.getUserId())) throw new IllegalArgumentException("Payment does not belong to this account");
        if (user.getCart() == null || user.getCart().size() != intent.getItems().size())
            throw new CheckoutChangedException();

        for (OrderItem item : intent.getItems()) {
            CartItem inCart = user.getCart().stream()
                    .filter(entry -> item.getBookId().equals(entry.getBookId())
                            && entry.getQuantity() == item.getQuantity()).findFirst().orElse(null);
            if (inCart == null) throw new CheckoutChangedException();
            if (!inCart.isStockReserved()) {
                Book book = bookRepository.findById(item.getBookId())
                        .orElseThrow(CheckoutChangedException::new);
                if (!book.isAvailable() || book.getStock() < item.getQuantity())
                    throw new CheckoutChangedException();
                book.setStock(book.getStock() - item.getQuantity());
                bookRepository.save(book);
            }
        }
        Instant now = Instant.now();
        Order order = new Order();
        order.setOrderNumber(makeOrderNumber());
        order.setUserId(user.getId());
        order.setCustomerName(user.getName());
        order.setCustomerEmail(user.getEmail());
        order.setCustomerMobile(user.getMobile());
        order.setItems(new ArrayList<>(intent.getItems()));
        order.setDeliveryAddress(userService.rememberCheckoutAddress(user, intent.getAddress()));
        order.setPaymentMethod(intent.getAmountPaise() == 0 ? "WALLET" : "ONLINE");
        order.setPaymentStatus("PAID");
        // Paid amount includes both Razorpay money and redeemed wallet credit.
        order.setAmountPaid((intent.getAmountPaise() + intent.getWalletRedeemedPaise()) / 100.0);
        order.setWalletRedeemedPaise(intent.getWalletRedeemedPaise());
        order.setRazorpayPaidPaise(intent.getAmountPaise());
        order.setRazorpayOrderId(intent.getRazorpayOrderId());
        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setStatus(BOOKING_CONFIRMED);
        order.setStatusHistory(new ArrayList<>(List.of(new OrderStatusEvent(
                BOOKING_CONFIRMED, intent.getAmountPaise() == 0 ? "Order paid with wallet" : "Order received and payment captured", now))));
        order.setSubtotal(intent.getSubtotalPaise() / 100.0);
        order.setShipping(intent.getShippingPaise() / 100.0);
        order.setDiscount(intent.getDiscountPaise() / 100.0);
        order.setBogoDiscountPaise(intent.getBogoDiscountPaise());
        order.setOfferCode(intent.getOfferCode());
        order.setTotal((intent.getAmountPaise() + intent.getWalletRedeemedPaise()) / 100.0);
        order.setCashbackEarnedPaise(intent.getCashbackPaise());
        order.setCashbackReversed(false);
        order.setStockRestored(false);
        order.setCreatedAt(now);
        order.setStatusUpdatedAt(now);
        Order saved = orderRepository.save(order);
        user.getCart().clear();
        userService.save(user);
        return saved;
    }

    public static class CheckoutChangedException extends IllegalStateException {
        public CheckoutChangedException() {
            super("Your cart or reserved stock changed after payment started. Payment captured; contact support for review/refund.");
        }
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

        BogoCalculator.Result bogo = bogoRules.snapshot(orderItems);
        double merchandiseDue = round(Math.max(0, subtotal - bogo.discountPaise() / 100.0));
        double shipping = merchandiseDue >= 499 ? 0 : 60;
        Offer appliedOffer = resolveOffer(request.couponCode(), merchandiseDue);
        double couponDiscount = appliedOffer == null ? 0 : calculateDiscount(appliedOffer, merchandiseDue);
        double discount = bogo.discountPaise() / 100.0 + couponDiscount;
        Instant now = Instant.now();

        Address checkoutAddress = userService.rememberCheckoutAddress(user, request.address());

        Order order = new Order();
        order.setOrderNumber(makeOrderNumber());
        order.setUserId(user.getId());
        order.setCustomerName(user.getName());
        order.setCustomerEmail(user.getEmail());
        order.setCustomerMobile(user.getMobile());
        order.setItems(orderItems);
        order.setDeliveryAddress(checkoutAddress);
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentStatus("UNPAID");
        order.setAmountPaid(0);
        order.setStatus(BOOKING_CONFIRMED);
        order.setStatusHistory(new ArrayList<>(List.of(
                new OrderStatusEvent(BOOKING_CONFIRMED, "Order received and booking confirmed", now)
        )));
        order.setSubtotal(round(subtotal));
        order.setShipping(shipping);
        order.setDiscount(round(discount));
        order.setBogoDiscountPaise(bogo.discountPaise());
        order.setOfferCode(appliedOffer == null ? null : appliedOffer.getCode());
        order.setTotal(round(Math.max(0, subtotal + shipping - discount)));
        order.setStockRestored(false);
        order.setCreatedAt(now);
        order.setStatusUpdatedAt(now);

        Order saved = orderRepository.save(order);

        user.getCart().clear();
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

    public synchronized Order updatePayment(String orderId, double amountPaid) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (Double.isNaN(amountPaid) || Double.isInfinite(amountPaid)) {
            throw new IllegalArgumentException("Amount paid must be a valid number");
        }
        if (amountPaid < 0) {
            throw new IllegalArgumentException("Amount paid cannot be negative");
        }
        if (amountPaid > order.getTotal()) {
            throw new IllegalArgumentException("Amount paid cannot be greater than the order total");
        }

        double paid = round(amountPaid);
        order.setAmountPaid(paid);
        if (paid <= 0) {
            order.setPaymentStatus("UNPAID");
        } else if (paid < order.getTotal()) {
            order.setPaymentStatus("PARTIALLY_PAID");
        } else {
            order.setPaymentStatus("PAID");
        }

        return orderRepository.save(order);
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

    @Transactional
    public synchronized Order updateStatus(String orderId, String newStatus, String note, String courierName, String trackingNumber) {
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
            order.setPaymentStatus("UNPAID");
            order.setAmountPaid(0);
            order.setCourierName(null);
            order.setTrackingNumber(null);
        } else if (!isAllowedTransition(current, status)) {
            throw new IllegalArgumentException("This status change is not allowed from " + statusLabel(current));
        }

        if (CANCELLED.equals(status) && !order.isStockRestored()) {
            // Wallet refund and cashback reversal are inside the same MongoDB transaction.
            // A Razorpay refund is a separate support/operations action.
            if (order.getWalletRedeemedPaise() > 0) {
                walletService.refundRedemption(order.getUserId(), order.getId(), order.getWalletRedeemedPaise());
            }
            // Cancellation removes promotional credit, but does NOT initiate a Razorpay refund.
            if (order.getCashbackEarnedPaise() > 0 && !order.isCashbackReversed()
                    && order.getRazorpayPaymentId() != null) {
                walletService.reverseCashback(order.getUserId(), order.getId(), order.getCashbackEarnedPaise());
                order.setCashbackReversed(true);
            }
            restoreOrderStock(order);
            order.setStockRestored(true);
            order.setCourierName(null);
            order.setTrackingNumber(null);
        }

        if (SHIPPED.equals(status)) {
            String courier = courierName == null ? "" : courierName.trim();
            String tracking = trackingNumber == null ? "" : trackingNumber.trim();
            if (courier.isBlank() || tracking.isBlank()) {
                throw new IllegalArgumentException("Courier name and tracking number are required when shipping an order");
            }
            order.setCourierName(courier);
            order.setTrackingNumber(tracking);
        }

        Instant now = Instant.now();
        order.setStatus(status);
        order.setStatusUpdatedAt(now);
        if (DELIVERED.equals(status)) {
            order.setAmountPaid(round(order.getTotal()));
            order.setPaymentStatus("PAID");
        }

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
