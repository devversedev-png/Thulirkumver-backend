package com.thulirkumver.bookstore.config;

import com.thulirkumver.bookstore.model.PaymentIntent;
import com.thulirkumver.bookstore.model.WalletTransaction;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/** Fail startup rather than run without the idempotency/ledger constraints. */
@Component
public class WalletIndexesConfig {
    private final MongoTemplate mongo;
    public WalletIndexesConfig(MongoTemplate mongo) { this.mongo = mongo; }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureUniqueIndexes() {
        mongo.indexOps(WalletTransaction.class).ensureIndex(
                new Index().on("referenceKey", Sort.Direction.ASC).unique());
        mongo.indexOps(PaymentIntent.class).ensureIndex(
                new Index().on("razorpayPaymentId", Sort.Direction.ASC).unique().sparse());
    }
}
