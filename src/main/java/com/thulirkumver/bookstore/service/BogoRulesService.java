package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.OrderItem;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Only server-side, active, admin-selected BOGO titles qualify. */
@Service
public class BogoRulesService {
    private final MongoTemplate mongo;
    public BogoRulesService(MongoTemplate mongo) { this.mongo = mongo; }

    public BogoCalculator.Result snapshot(List<OrderItem> items) {
        Document section = mongo.getCollection("home_sections")
                .find(new Document("key", new Document("$regex", "^" + Pattern.quote("bogo") + "$")
                        .append("$options", "i"))).first();
        Set<String> selected = new HashSet<>();
        if (section != null && !"false".equalsIgnoreCase(String.valueOf(section.get("active")))) {
            Object rawConfig = section.get("config");
            Object enabled = rawConfig instanceof Map<?, ?> config ? config.get("enabled") : null;
            if (!"false".equalsIgnoreCase(String.valueOf(enabled)) && section.get("bookIds") instanceof List<?> ids) {
                for (Object id : ids) if (id != null) selected.add(String.valueOf(id));
            }
        }
        return BogoCalculator.calculate(items, selected);
    }
}
