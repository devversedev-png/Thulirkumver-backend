package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.HomeSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HomeSettingsRepository extends MongoRepository<HomeSettings, String> {
}
