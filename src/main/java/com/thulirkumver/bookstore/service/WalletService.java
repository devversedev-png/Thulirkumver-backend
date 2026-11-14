package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.Wallet;
import com.thulirkumver.bookstore.model.WalletTransaction;
import com.thulirkumver.bookstore.repository.WalletRepository;
import com.thulirkumver.bookstore.repository.WalletTransactionRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final MongoTemplate mongo;

    public WalletService(WalletRepository wallets, WalletTransactionRepository transactions, MongoTemplate mongo) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.mongo = mongo;
    }

    public long balancePaise(String userId) {
        return wallets.findById(userId).map(Wallet::getBalancePaise).orElse(0L);
    }

    public List<WalletTransaction> history(String userId) {
        return transactions.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Must be invoked only after server-side verification of a captured payment and order ownership.
     * Requires MongoDB transactions (Atlas replica set) and a MongoTransactionManager.
     * A unique index on referenceKey makes retries safe, including across app instances.
     */
    @Transactional
    public void creditCashback(String userId, String orderId, long cashbackPaise) {
        if (userId == null || userId.isBlank() || orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("Missing wallet owner or order");
        if (cashbackPaise < 0) throw new IllegalArgumentException("Negative cashback");
        if (cashbackPaise == 0) return;

        String key = "CASHBACK:" + orderId;
        if (transactions.findByReferenceKey(key).isPresent()) return;

        WalletTransaction entry = new WalletTransaction();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setReferenceKey(key);
        entry.setType("CASHBACK_CREDIT");
        entry.setAmountPaise(cashbackPaise);
        entry.setDescription("Cashback for order " + orderId);
        entry.setCreatedAt(Instant.now());
        // Duplicate-key exceptions must roll back the transaction; caller may re-read the ledger.
        transactions.save(entry);

        mongo.findAndModify(
                Query.query(Criteria.where("_id").is(userId)),
                new Update().inc("balancePaise", cashbackPaise).set("updatedAt", Instant.now())
                        .setOnInsert("userId", userId),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Wallet.class
        );
    }
    public static class InsufficientWalletBalanceException extends IllegalStateException {
        public InsufficientWalletBalanceException() {
            super("Wallet balance changed during checkout. If Razorpay captured payment, contact support for reconciliation/refund.");
        }
    }

    /** Debit exactly once, atomically with the bookstore order and cashback credit. */
    @Transactional
    public void redeemForOrder(String userId, String orderId, long amountPaise) {
        if (amountPaise <= 0) return;
        String key = "WALLET_REDEMPTION:" + orderId;
        if (transactions.findByReferenceKey(key).isPresent()) return;

        Wallet result = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(userId).and("balancePaise").gte(amountPaise)),
                new Update().inc("balancePaise", -amountPaise).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Wallet.class);
        if (result == null) throw new InsufficientWalletBalanceException();

        WalletTransaction entry = new WalletTransaction();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setReferenceKey(key);
        entry.setType("WALLET_REDEMPTION");
        entry.setAmountPaise(-amountPaise);
        entry.setDescription("Wallet used for order " + orderId);
        entry.setCreatedAt(Instant.now());
        transactions.save(entry);
    }

    /** Refund the wallet portion on cancellation, once per order. */
    @Transactional
    public void refundRedemption(String userId, String orderId, long amountPaise) {
        if (amountPaise <= 0) return;
        if (transactions.findByReferenceKey("WALLET_REDEMPTION:" + orderId).isEmpty()) return;
        String key = "WALLET_REDEMPTION_REFUND:" + orderId;
        if (transactions.findByReferenceKey(key).isPresent()) return;
        WalletTransaction entry = new WalletTransaction();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setReferenceKey(key);
        entry.setType("WALLET_REDEMPTION_REFUND");
        entry.setAmountPaise(amountPaise);
        entry.setDescription("Wallet redemption refunded for cancelled order " + orderId);
        entry.setCreatedAt(Instant.now());
        transactions.save(entry);
        mongo.findAndModify(
                Query.query(Criteria.where("_id").is(userId)),
                new Update().inc("balancePaise", amountPaise).set("updatedAt", Instant.now())
                        .setOnInsert("userId", userId),
                FindAndModifyOptions.options().upsert(true).returnNew(true), Wallet.class);
    }

    /** Called from a transaction when an online order is cancelled. The actual payment refund is separate. */
    @Transactional
    public void reverseCashback(String userId, String orderId, long cashbackPaise) {
        if (cashbackPaise <= 0) return;
        String creditKey = "CASHBACK:" + orderId;
        if (transactions.findByReferenceKey(creditKey).isEmpty()) return;
        String key = "CASHBACK_REVERSAL:" + orderId;
        if (transactions.findByReferenceKey(key).isPresent()) return;
        WalletTransaction entry = new WalletTransaction();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setReferenceKey(key);
        entry.setType("CASHBACK_REVERSAL");
        entry.setAmountPaise(-cashbackPaise);
        entry.setDescription("Cashback reversed for cancelled order " + orderId);
        entry.setCreatedAt(Instant.now());
        transactions.save(entry);
        Wallet updated = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(userId).and("balancePaise").gte(cashbackPaise)),
                new Update().inc("balancePaise", -cashbackPaise).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Wallet.class);
        if (updated == null) throw new IllegalStateException("Cannot reverse cashback: insufficient wallet balance");
    }

}
