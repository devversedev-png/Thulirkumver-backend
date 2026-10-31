package com.thulirkumver.bookstore.integration.amazon;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AmazonSyncStateRepository extends MongoRepository<AmazonSyncState, String> {
}
