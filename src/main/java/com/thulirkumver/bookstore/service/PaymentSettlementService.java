package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.Order;
import com.thulirkumver.bookstore.model.Address;
import com.thulirkumver.bookstore.model.PaymentIntent;
import com.thulirkumver.bookstore.repository.OrderRepository;
import com.thulirkumver.bookstore.repository.PaymentIntentRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettlementService {
    private final PaymentIntentRepository intents;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final WalletService wallets;
    private final MongoTemplate mongo;

    public PaymentSettlementService(PaymentIntentRepository intents, OrderRepository orders,
            OrderService orderService, WalletService wallets, MongoTemplate mongo) {
        this.intents = intents;
        this.orders = orders;
        this.orderService = orderService;
        this.wallets = wallets;
        this.mongo = mongo;
    }

    public long walletBalancePaise(String userId) { return wallets.balancePaise(userId); }

    /** A fully wallet-funded checkout never calls Razorpay. No money leaves the wallet
     * unless the order can also be persisted and the cart cleared in this transaction. */
    @Transactional
    public Order settleWalletOnly(String email, String userId, Address address, String couponCode) {
        PaymentIntent intent = orderService.prepareOnlineCheckout(email, address, couponCode, true);
        if (!userId.equals(intent.getUserId())) throw new IllegalArgumentException("Wrong wallet owner");
        if (intent.getWalletRedeemedPaise() <= 0 || intent.getAmountPaise() != 0) {
            throw new IllegalArgumentException("Wallet does not cover the entire order. Continue with Razorpay.");
        }
        Order order = orderService.placeCapturedOrder(email, intent, null);
        wallets.redeemForOrder(userId, order.getId(), intent.getWalletRedeemedPaise());
        // Fully wallet-funded orders are intentionally not cashback-eligible.
        return order;
    }

    /** Payment signature, ownership, captured status and amount are validated in the controller. */
    @Transactional
    public Order settle(String email, String userId, String razorpayOrderId, String paymentId) {
        PaymentIntent intent = intents.findById(razorpayOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Razorpay order"));
        if (!userId.equals(intent.getUserId()))
            throw new IllegalArgumentException("Payment does not belong to this account");
        if ("COMPLETED".equals(intent.getStatus())) {
            if (!paymentId.equals(intent.getRazorpayPaymentId()))
                throw new IllegalArgumentException("Razorpay payment ID does not match this order");
            return orders.findById(intent.getBookstoreOrderId())
                    .orElseThrow(() -> new IllegalStateException("Completed payment has no bookstore order"));
        }
        if (!"CREATED".equals(intent.getStatus()))
            throw new IllegalStateException("This captured payment requires support review");

        // Write-conflict protects concurrent callbacks across multiple backend instances.
        PaymentIntent claimed = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(razorpayOrderId).and("status").is("CREATED")),
                new Update().set("status", "PROCESSING").set("razorpayPaymentId", paymentId),
                FindAndModifyOptions.options().returnNew(true), PaymentIntent.class);
        if (claimed == null) throw new IllegalStateException("Payment verification is already being processed; retry");

        Order order = orderService.placeCapturedOrder(email, claimed, paymentId);
        // Same transaction as creating order and clearing cart: no partial cashback credits.
        wallets.redeemForOrder(userId, order.getId(), claimed.getWalletRedeemedPaise());
        wallets.creditCashback(userId, order.getId(), claimed.getCashbackPaise());
        claimed.setStatus("COMPLETED");
        claimed.setBookstoreOrderId(order.getId());
        intents.save(claimed);
        return order;
    }
}
