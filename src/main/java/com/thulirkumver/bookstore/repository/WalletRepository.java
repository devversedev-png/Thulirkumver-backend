package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.Wallet;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WalletRepository extends MongoRepository<Wallet, String> {}
