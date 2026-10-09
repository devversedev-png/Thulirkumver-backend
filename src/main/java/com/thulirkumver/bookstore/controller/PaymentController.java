package com.thulirkumver.bookstore.controller;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.thulirkumver.bookstore.config.RazorpayConfig.RazorpayProperties;
import com.thulirkumver.bookstore.dto.CheckoutRequest;
import com.thulirkumver.bookstore.dto.RazorpayOrderRequest;
import com.thulirkumver.bookstore.dto.RazorpayOrderResponse;
import com.thulirkumver.bookstore.dto.RazorpayVerifyRequest;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.service.OrderService;
import jakarta.validation.Valid;
import org.json.JSONObject;
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

    public PaymentController(
            RazorpayClient razorpayClient,
            RazorpayProperties razorpayProperties,
            OrderService orderService,
            OrderRepository orderRepository
    ) {
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            Authentication authentication,
            @Valid @RequestBody RazorpayOrderRequest request
    ) {
        try {
            double total = orderService.calculateCheckoutTotal(
                    authentication.getName(),
                    request.address(),
                    request.couponCode()
            );

            long amountInPaise = Math.round(total * 100);
            if (amountInPaise <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid payment amount"));
            }

            JSONObject options = new JSONObject();
            options.put("amount", amountInPaise);
            options.put("currency", "INR");
            options.put("receipt", "TVB-" + UUID.randomUUID());

            Order razorpayOrder = razorpayClient.orders.create(options);

            return ResponseEntity.ok(new RazorpayOrderResponse(
                    razorpayOrder.get("id"),
                    razorpayProperties.keyId(),
                    amountInPaise,
                    "INR"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unable to create Razorpay payment order"));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            Authentication authentication,
            @Valid @RequestBody RazorpayVerifyRequest request
    ) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.razorpayOrderId());
            attributes.put("razorpay_payment_id", request.razorpayPaymentId());
            attributes.put("razorpay_signature", request.razorpaySignature());

            boolean verified = Utils.verifyPaymentSignature(
                    attributes,
                    razorpayProperties.keySecret()
            );

            if (!verified) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid payment signature"
                ));
            }

            // Avoid creating the same bookstore order twice if the verification callback is repeated.
            var existing = orderRepository.findByRazorpayPaymentId(request.razorpayPaymentId());
            if (existing.isPresent()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Payment already verified",
                        "paymentId", request.razorpayPaymentId(),
                        "order", existing.get()
                ));
            }

            // One Razorpay call: the payment carries both order_id and amount.
            Payment razorpayPayment = razorpayClient.payments.fetch(request.razorpayPaymentId());
            String paymentOrderId = String.valueOf(razorpayPayment.get("order_id"));
            String paymentStatus = String.valueOf(razorpayPayment.get("status"));
            long paymentAmount = Long.parseLong(String.valueOf(razorpayPayment.get("amount")));

            if (!request.razorpayOrderId().equals(paymentOrderId)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Payment does not belong to this Razorpay order"
                ));
            }

            if (!"captured".equalsIgnoreCase(paymentStatus)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Payment has not been captured yet"
                ));
            }

            double expectedTotal = orderService.calculateCheckoutTotal(
                    authentication.getName(),
                    request.address(),
                    request.couponCode()
            );
            long expectedAmount = Math.round(expectedTotal * 100);

            if (paymentAmount != expectedAmount) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Payment amount does not match the current order total"
                ));
            }

            CheckoutRequest checkoutRequest = new CheckoutRequest(
                    request.address(),
                    "ONLINE",
                    request.couponCode()
            );
            com.thulirkumver.bookstore.model.Order savedOrder =
                    orderService.placeOrder(authentication.getName(), checkoutRequest);

            savedOrder.setPaymentStatus("PAID");
            savedOrder.setAmountPaid(savedOrder.getTotal());
            savedOrder.setRazorpayOrderId(request.razorpayOrderId());
            savedOrder.setRazorpayPaymentId(request.razorpayPaymentId());
            savedOrder = orderRepository.save(savedOrder);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment verified successfully",
                    "paymentId", request.razorpayPaymentId(),
                    "order", savedOrder
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Payment verification failed. Please contact support if your account was charged."
            ));
        }
    }
}
