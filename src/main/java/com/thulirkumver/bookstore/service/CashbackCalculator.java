package com.thulirkumver.bookstore.service;

import com.thulirkumver.bookstore.model.HomeSection;
import com.thulirkumver.bookstore.model.OrderItem;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Calculates earned cashback from a snapshot of purchased items and saved rules. */
@Component
public class CashbackCalculator {
    public long calculatePaise(List<OrderItem> items, HomeSection section) {
        if (section == null || !section.isActive() || !"cashback".equalsIgnoreCase(section.getKey())
                || section.getConfig() == null || items == null) return 0L;
        Object raw = section.getConfig().get("perBookCashbacks");
        if (!(raw instanceof Map<?, ?> entries)) return 0L;
        Set<String> selected = new HashSet<>(section.getBookIds() == null ? List.of() : section.getBookIds());
        long sum = 0L;
        for (OrderItem item : items) {
            if (item == null || item.getBookId() == null || !selected.contains(item.getBookId())
                    || item.getQuantity() <= 0 || item.getPrice() <= 0) continue;
            Object value = entries.get(item.getBookId());
            if (!(value instanceof Map<?, ?> rule)) continue;
            String type = String.valueOf(rule.get("type"));
            BigDecimal configured;
            try { configured = new BigDecimal(String.valueOf(rule.get("value"))); }
            catch (Exception ex) { continue; }
            if (configured.signum() <= 0) continue;
            BigDecimal unitPrice = BigDecimal.valueOf(item.getPrice());
            BigDecimal cashback;
            if ("PERCENT".equalsIgnoreCase(type)) {
                if (configured.compareTo(BigDecimal.valueOf(100)) > 0) continue;
                cashback = unitPrice.multiply(configured).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            } else if ("FIXED".equalsIgnoreCase(type)) {
                cashback = configured;
            } else continue;
            cashback = cashback.min(unitPrice);
            long itemPaise = cashback.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            sum = Math.addExact(sum, itemPaise);
        }
        return sum;
    }
}
