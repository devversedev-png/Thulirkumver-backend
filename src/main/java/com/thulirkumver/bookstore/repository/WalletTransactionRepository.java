package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.WalletTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository extends MongoRepository<WalletTransaction, String> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<WalletTransaction> findByReferenceKey(String referenceKey);
}
