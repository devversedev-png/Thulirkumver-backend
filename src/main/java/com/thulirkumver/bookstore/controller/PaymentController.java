package com.thulirkumver.bookstore.controller;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.thulirkumver.bookstore.config.RazorpayConfig.RazorpayProperties;
import com.thulirkumver.bookstore.dto.RazorpayOrderRequest;
import com.thulirkumver.bookstore.dto.RazorpayOrderResponse;
import com.thulirkumver.bookstore.dto.RazorpayVerifyRequest;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;

    public PaymentController(
            RazorpayClient razorpayClient,
            RazorpayProperties razorpayProperties
    ) {
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
    }

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @RequestBody RazorpayOrderRequest request
    ) {

        try {

            if (request.amount() <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Invalid payment amount"
                        ));
            }

            long amountInPaise =
                    Math.round(request.amount() * 100);

            JSONObject options = new JSONObject();

            options.put(
                    "amount",
                    amountInPaise
            );

            options.put(
                    "currency",
                    "INR"
            );

            options.put(
                    "receipt",
                    "TVB-" + UUID.randomUUID()
            );

            Order order =
                    razorpayClient.orders.create(options);

            return ResponseEntity.ok(
                    new RazorpayOrderResponse(
                            order.get("id"),
                            razorpayProperties.keyId(),
                            amountInPaise,
                            "INR"
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to create payment order"
                    ));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestBody RazorpayVerifyRequest request
    ) {

        try {

            JSONObject attributes =
                    new JSONObject();

            attributes.put(
                    "razorpay_order_id",
                    request.razorpayOrderId()
            );

            attributes.put(
                    "razorpay_payment_id",
                    request.razorpayPaymentId()
            );

            attributes.put(
                    "razorpay_signature",
                    request.razorpaySignature()
            );

            boolean verified =
                    Utils.verifyPaymentSignature(
                            attributes,
                            razorpayProperties.keySecret()
                    );

            if (!verified) {

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success",
                                false,
                                "message",
                                "Invalid payment signature"
                        ));
            }

            return ResponseEntity.ok(
                    Map.of(
                            "success",
                            true,
                            "message",
                            "Payment verified successfully",
                            "paymentId",
                            request.razorpayPaymentId()
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success",
                            false,
                            "message",
                            "Payment verification failed"
                    ));
        }
    }
}