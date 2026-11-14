package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.HomeSection;
import com.thulirkumver.bookstore.model.OrderItem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CashbackRulesService {
    private final MongoTemplate mongo;
    private final CashbackCalculator calculator;

    public CashbackRulesService(MongoTemplate mongo, CashbackCalculator calculator) {
        this.mongo = mongo;
        this.calculator = calculator;
    }

    /** Capture the rule at checkout, not later at payment verification. */
    public long snapshotCashbackPaise(List<OrderItem> items) {
        Document document = mongo.getCollection("home_sections")
                .find(new Document("key", new Document("$regex", "^" + Pattern.quote("cashback") + "$")
                        .append("$options", "i"))).first();
        if (document == null) return 0;
        HomeSection section = new HomeSection();
        section.setKey("cashback");
        Object active = document.get("active");
        section.setActive(active == null || Boolean.parseBoolean(String.valueOf(active)));
        List<String> ids = new ArrayList<>();
        if (document.get("bookIds") instanceof List<?> values) {
            for (Object v : values) if (v != null) ids.add(String.valueOf(v));
        }
        section.setBookIds(ids);
        Map<String, Object> config = new HashMap<>();
        if (document.get("config") instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null) config.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        section.setConfig(config);
        return calculator.calculatePaise(items, section);
    }
}
