package com.thulirkumver.bookstore.controller;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.thulirkumver.bookstore.config.RazorpayConfig.RazorpayProperties;
import com.thulirkumver.bookstore.dto.RazorpayOrderRequest;
import com.thulirkumver.bookstore.dto.CheckoutQuoteRequest;
import com.thulirkumver.bookstore.dto.CheckoutQuoteResponse;
import com.thulirkumver.bookstore.service.WalletService;
import com.thulirkumver.bookstore.dto.RazorpayOrderResponse;
import com.thulirkumver.bookstore.dto.RazorpayVerifyRequest;
import com.thulirkumver.bookstore.model.PaymentIntent;
import com.thulirkumver.bookstore.model.User;
import com.thulirkumver.bookstore.repository.PaymentIntentRepository;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.service.OrderService;
import com.thulirkumver.bookstore.service.PaymentSettlementService;
import com.thulirkumver.bookstore.service.UserService;
import jakarta.validation.Valid;
import org.json.JSONObject;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentIntentRepository intents;
    private final PaymentSettlementService settlement;
    private final UserService users;
    private final MongoTemplate mongo;

    public PaymentController(RazorpayClient razorpayClient, RazorpayProperties razorpayProperties,
            OrderService orderService, OrderRepository orderRepository,
            PaymentIntentRepository intents, PaymentSettlementService settlement,
            UserService users, MongoTemplate mongo) {
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.intents = intents;
        this.settlement = settlement;
        this.users = users;
        this.mongo = mongo;
    }

    @PostMapping("/quote")
    public ResponseEntity<?> quote(Authentication authentication,
            @RequestBody CheckoutQuoteRequest request) {
        try {
            User user = users.getByEmail(authentication.getName());
            PaymentIntent quote = orderService.prepareOnlineCheckout(
                    authentication.getName(), new com.thulirkumver.bookstore.model.Address(),
                    request.couponCode(), request.useWallet());
            long gross = quote.getAmountPaise() + quote.getWalletRedeemedPaise();
            return ResponseEntity.ok(new CheckoutQuoteResponse(
                    quote.getSubtotalPaise(), quote.getShippingPaise(), quote.getDiscountPaise(),
                    gross, Math.max(0, settlement.walletBalancePaise(user.getId())),
                    quote.getWalletRedeemedPaise(), quote.getAmountPaise(), quote.getCashbackPaise(), "INR", quote.getBogoDiscountPaise()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/wallet-checkout")
    public ResponseEntity<?> walletCheckout(Authentication authentication,
            @Valid @RequestBody RazorpayOrderRequest request) {
        try {
            if (!request.useWallet())
                throw new IllegalArgumentException("Please select Use Wallet first");
            User user = users.getByEmail(authentication.getName());
            var order = settlement.settleWalletOnly(authentication.getName(), user.getId(),
                    request.address(), request.couponCode());
            return ResponseEntity.ok(Map.of("success", true, "message", "Order paid with wallet", "order", order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (WalletService.InsufficientWalletBalanceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false,
                    "message", "Wallet checkout could not be finalized. Please check your orders before retrying."));
        }
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(Authentication authentication,
            @Valid @RequestBody RazorpayOrderRequest request) {
        try {
            PaymentIntent intent = orderService.prepareOnlineCheckout(
                    authentication.getName(), request.address(), request.couponCode(), request.useWallet());
            if (intent.getAmountPaise() == 0) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Wallet covers the entire amount. Use wallet checkout instead."));
            }
            JSONObject options = new JSONObject();
            options.put("amount", intent.getAmountPaise());
            options.put("currency", "INR");
            options.put("receipt", "TVB-" + UUID.randomUUID());
            Order razorpayOrder = razorpayClient.orders.create(options);
            intent.setRazorpayOrderId(razorpayOrder.get("id"));
            intents.save(intent);
            return ResponseEntity.ok(new RazorpayOrderResponse(
                    intent.getRazorpayOrderId(), razorpayProperties.keyId(), intent.getAmountPaise(), "INR"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to create Razorpay payment order"));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(Authentication authentication,
            @Valid @RequestBody RazorpayVerifyRequest request) {
        try {
            User user = users.getByEmail(authentication.getName());
            PaymentIntent intent = intents.findById(request.razorpayOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown Razorpay order"));
            if (!user.getId().equals(intent.getUserId()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Payment does not belong to this account"));

            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.razorpayOrderId());
            attributes.put("razorpay_payment_id", request.razorpayPaymentId());
            attributes.put("razorpay_signature", request.razorpaySignature());
            if (!Utils.verifyPaymentSignature(attributes, razorpayProperties.keySecret()))
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid payment signature"));

            Payment payment = razorpayClient.payments.fetch(request.razorpayPaymentId());
            String actualOrder = String.valueOf(payment.get("order_id"));
            String status = String.valueOf(payment.get("status"));
            String currency = String.valueOf(payment.get("currency"));
            long amountPaise = Long.parseLong(String.valueOf(payment.get("amount")));
            if (!request.razorpayOrderId().equals(actualOrder) || !"INR".equalsIgnoreCase(currency)
                    || amountPaise != intent.getAmountPaise())
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Captured payment does not match saved Razorpay checkout"));
            if (!"captured".equalsIgnoreCase(status))
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Payment has not been captured yet"));

            // An old paid order is safe to return only for this authenticated owner and payment.
            var already = orderRepository.findByRazorpayPaymentId(request.razorpayPaymentId());
            if (already.isPresent()) {
                if (!user.getId().equals(already.get().getUserId())
                        || !request.razorpayOrderId().equals(already.get().getRazorpayOrderId()))
                    throw new IllegalArgumentException("Payment belongs to another order");
                return ResponseEntity.ok(Map.of("success", true, "message", "Payment already verified",
                        "paymentId", request.razorpayPaymentId(), "order", already.get()));
            }

            var order = settlement.settle(authentication.getName(), user.getId(),
                    request.razorpayOrderId(), request.razorpayPaymentId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Payment verified successfully",
                    "paymentId", request.razorpayPaymentId(), "order", order));
        } catch (WalletService.InsufficientWalletBalanceException e) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(request.razorpayOrderId())
                            .and("status").is("CREATED")),
                    new Update().set("status", "REVIEW_REQUIRED")
                            .set("razorpayPaymentId", request.razorpayPaymentId()), PaymentIntent.class);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false,
                    "message", "Payment captured but wallet balance changed. Contact support for manual reconciliation/refund."));
        } catch (OrderService.CheckoutChangedException e) {
            // The transaction rolled back. Mark for manual fulfilment/refund reconciliation.
            mongo.updateFirst(Query.query(Criteria.where("_id").is(request.razorpayOrderId())
                            .and("status").is("CREATED")),
                    new Update().set("status", "REVIEW_REQUIRED")
                            .set("razorpayPaymentId", request.razorpayPaymentId()), PaymentIntent.class);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            // Captured payment is durable at Razorpay; retry verification safely.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false,
                    "message", "Payment captured but order could not be finalized. Retry verification or contact support."));
        }
    }
}
