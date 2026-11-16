package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.DeliverySettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeliverySettingsRepository extends MongoRepository<DeliverySettings, String> {}
