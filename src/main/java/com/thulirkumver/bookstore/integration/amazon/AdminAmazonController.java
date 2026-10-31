package com.thulirkumver.bookstore.integration.amazon;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin-only (covered by the /api/admin/** ADMIN rule in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/amazon")
public class AdminAmazonController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AmazonOrderSyncService syncService;
    private final AmazonOrderRepository orderRepository;

    public AdminAmazonController(AmazonOrderSyncService syncService, AmazonOrderRepository orderRepository) {
        this.syncService = syncService;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return statusBody(syncService.state());
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            return ResponseEntity.ok(statusBody(syncService.sync()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/orders")
    public List<AmazonOrder> orders(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) String q) {
        Instant fromTime = from == null || from.isBlank() ? null
                : LocalDate.parse(from).atStartOfDay(IST).toInstant();
        Instant toTime = to == null || to.isBlank() ? null
                : LocalDate.parse(to).plusDays(1).atStartOfDay(IST).toInstant();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        return orderRepository.findAllByOrderByCreatedTimeDesc().stream()
                .filter(o -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(o.getFulfillmentStatus()))
                .filter(o -> fromTime == null || (o.getCreatedTime() != null && !o.getCreatedTime().isBefore(fromTime)))
                .filter(o -> toTime == null || (o.getCreatedTime() != null && o.getCreatedTime().isBefore(toTime)))
                .filter(o -> needle.isEmpty() || matches(o, needle))
                .limit(1000)
                .toList();
    }

    private Map<String, Object> statusBody(AmazonSyncState state) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", syncService.client().isEnabled());
        body.put("configured", syncService.client().isConfigured());
        body.put("lastSuccessfulSync", state.getLastSuccessfulSync());
        body.put("lastAttempt", state.getLastAttempt());
        body.put("lastError", state.getLastError());
        body.put("lastSyncedCount", state.getLastSyncedCount());
        body.put("totalOrders", orderRepository.count());
        return body;
    }

    private boolean matches(AmazonOrder o, String needle) {
        return contains(o.getAmazonOrderId(), needle)
                || contains(o.getBuyerName(), needle)
                || contains(o.getRecipientName(), needle)
                || contains(o.getPhone(), needle)
                || contains(o.getCity(), needle)
                || contains(o.getPostalCode(), needle)
                || o.getItems().stream().anyMatch(i -> contains(i.title, needle) || contains(i.asin, needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
