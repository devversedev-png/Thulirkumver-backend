package com.thulirkumver.bookstore.integration.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls changed Amazon orders into MongoDB. Runs on a schedule and on demand
 * from the admin page. Orders are upserted by Amazon order id, so repeating a
 * window never creates duplicates.
 */
@Service
public class AmazonOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(AmazonOrderSyncService.class);
    private static final int MAX_PAGES_PER_RUN = 50;
    private static final Duration OVERLAP = Duration.ofMinutes(5);
    private static final Duration MIN_GAP_BETWEEN_RUNS = Duration.ofSeconds(60);

    private final AmazonSpApiClient client;
    private final AmazonOrderRepository orders;
    private final AmazonSyncStateRepository stateRepository;
    private final int initialLookbackDays;
    private final boolean storeRaw;

    public AmazonOrderSyncService(
            AmazonSpApiClient client,
            AmazonOrderRepository orders,
            AmazonSyncStateRepository stateRepository,
            @Value("${amazon.initial-lookback-days:30}") int initialLookbackDays,
            @Value("${amazon.store-raw:false}") boolean storeRaw
    ) {
        this.client = client;
        this.orders = orders;
        this.stateRepository = stateRepository;
        this.initialLookbackDays = initialLookbackDays;
        this.storeRaw = storeRaw;
    }

    public AmazonSpApiClient client() {
        return client;
    }

    public AmazonSyncState state() {
        return stateRepository.findById(AmazonSyncState.ORDERS).orElseGet(AmazonSyncState::new);
    }

    @Scheduled(
            fixedDelayString = "${amazon.sync-interval-ms:900000}",
            initialDelayString = "${amazon.sync-initial-delay-ms:60000}"
    )
    public void scheduledSync() {
        if (!client.isConfigured()) {
            return;
        }
        try {
            sync();
        } catch (Exception ex) {
            log.warn("Scheduled Amazon sync failed: {}", ex.getMessage());
        }
    }

    public synchronized AmazonSyncState sync() {
        if (!client.isConfigured()) {
            throw new IllegalStateException("Amazon integration is not configured on the server");
        }

        AmazonSyncState state = state();
        Instant now = Instant.now();

        if (state.getLastAttempt() != null
                && Duration.between(state.getLastAttempt(), now).compareTo(MIN_GAP_BETWEEN_RUNS) < 0) {
            return state; // Amazon throttles searchOrders hard; don't hammer it.
        }

        state.setLastAttempt(now);
        Instant from = state.getSyncedUntil() != null
                ? state.getSyncedUntil().minus(OVERLAP)
                : now.minus(Duration.ofDays(initialLookbackDays));

        int count = 0;
        try {
            String token = null;
            for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
                AmazonSpApiClient.OrdersPage result = client.searchOrders(from, token);
                List<AmazonOrder> batch = new ArrayList<>();
                for (JsonNode node : result.orders()) {
                    AmazonOrder mapped = map(node, now);
                    if (mapped != null) batch.add(mapped);
                }
                count += upsert(batch);
                token = result.nextToken();
                if (token == null) break;
            }

            state.setSyncedUntil(now);
            state.setLastSuccessfulSync(Instant.now());
            state.setLastSyncedCount(count);
            state.setLastError(null);
        } catch (AmazonSpApiClient.AmazonApiException ex) {
            state.setLastError(ex.getStatus() == 429
                    ? "Amazon is rate limiting requests. Sync will retry automatically."
                    : "Amazon returned " + ex.getStatus() + ": " + ex.getMessage());
            log.warn("Amazon sync failed: {}", state.getLastError());
        } catch (Exception ex) {
            state.setLastError("Sync failed: " + ex.getMessage());
            log.warn("Amazon sync failed", ex);
        }

        return stateRepository.save(state);
    }

    private int upsert(List<AmazonOrder> batch) {
        int saved = 0;
        for (AmazonOrder incoming : batch) {
            orders.findByAmazonOrderId(incoming.getAmazonOrderId())
                    .ifPresent(existing -> incoming.setId(existing.getId()));
            orders.save(incoming);
            saved++;
        }
        return saved;
    }

    // ---- mapping (defensive: fields vary with includedData and PII roles) ----

    AmazonOrder map(JsonNode n, Instant syncedAt) {
        String orderId = text(n, "orderId");
        if (orderId == null) return null;

        AmazonOrder o = new AmazonOrder();
        o.setAmazonOrderId(orderId);
        o.setCreatedTime(time(text(n, "createdTime")));
        o.setLastUpdatedTime(time(text(n, "lastUpdatedTime")));
        o.setMarketplaceId(text(n.path("salesChannel"), "marketplaceId"));
        o.setMarketplaceName(text(n.path("salesChannel"), "marketplaceName"));
        o.setFulfillmentStatus(text(n.path("fulfillment"), "fulfillmentStatus"));
        o.setFulfilledBy(text(n.path("fulfillment"), "fulfilledBy"));

        JsonNode buyer = n.path("buyer");
        o.setBuyerName(text(buyer, "buyerName"));
        o.setBuyerEmail(text(buyer, "buyerEmail"));

        JsonNode recipient = n.path("recipient");
        JsonNode address = recipient.path("deliveryAddress");
        o.setRecipientName(first(text(address, "name"), text(recipient, "name")));
        o.setPhone(first(text(address, "phone"), text(recipient, "phone")));
        o.setAddressLine1(text(address, "addressLine1"));
        o.setAddressLine2(joinNonBlank(text(address, "addressLine2"), text(address, "addressLine3")));
        o.setCity(text(address, "city"));
        o.setState(text(address, "stateOrRegion"));
        o.setPostalCode(text(address, "postalCode"));
        o.setCountryCode(text(address, "countryCode"));

        double total = 0;
        String currency = null;
        List<AmazonOrder.Item> items = new ArrayList<>();
        for (JsonNode itemNode : n.path("orderItems")) {
            AmazonOrder.Item item = new AmazonOrder.Item();
            JsonNode product = itemNode.path("product");
            item.orderItemId = text(itemNode, "orderItemId");
            item.asin = text(product, "asin");
            item.sku = first(text(product, "sellerSku"), text(product, "sku"));
            item.title = text(product, "title");
            item.quantity = itemNode.path("quantityOrdered").asInt(itemNode.path("quantity").asInt(1));

            // Line total: prefer the proceeds ITEM subtotal, else unit price x quantity.
            JsonNode itemSubtotal = null;
            for (JsonNode b : itemNode.path("proceeds").path("breakdowns")) {
                if ("ITEM".equalsIgnoreCase(b.path("type").asText())) {
                    itemSubtotal = b.path("subtotal");
                    break;
                }
            }
            if (itemSubtotal != null && itemSubtotal.has("amount")) {
                item.lineTotal = amount(itemSubtotal.path("amount"));
                currency = first(currency, text(itemSubtotal, "currencyCode"));
            } else if (product.path("price").has("amount")) {
                item.lineTotal = amount(product.path("price").path("amount")) * item.quantity;
                currency = first(currency, text(product.path("price"), "currencyCode"));
            }
            total += item.lineTotal;
            items.add(item);
        }
        o.setItems(items);
        o.setItemsTotal(Math.round(total * 100.0) / 100.0);
        o.setCurrency(first(currency, "INR"));
        o.setSyncedAt(syncedAt);
        if (storeRaw) o.setRawJson(n.toString());
        return o;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static double amount(JsonNode v) {
        if (v.isNumber()) return v.asDouble();
        try {
            return Double.parseDouble(v.asText("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Instant time(String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(iso);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static String first(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String joinNonBlank(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + ", " + b;
    }
}
