package com.thulirkumver.bookstore.integration.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Selling Partner API client for the Orders API v2026-01-01.
 *
 * Auth is Login with Amazon (LWA): a long-lived refresh token is exchanged for a
 * one-hour access token. SP-API no longer needs AWS SigV4 signing, and Orders
 * v2026-01-01 returns buyer/recipient data directly (no Restricted Data Token).
 *
 * searchOrders is heavily throttled (about one request every three minutes
 * sustained, burst of 20), so nothing here is called per page view. Orders are
 * synced into MongoDB on a schedule and the admin screen reads from there.
 */
@Component
public class AmazonSpApiClient {

    private static final Logger log = LoggerFactory.getLogger(AmazonSpApiClient.class);
    private static final String LWA_TOKEN_URL = "https://api.amazon.com/auth/o2/token";

    public record OrdersPage(List<JsonNode> orders, String nextToken) {
    }

    public static class AmazonApiException extends RuntimeException {
        private final int status;

        public AmazonApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    private final RestClient http;
    private final RestClient lwa = RestClient.create();
    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final List<String> marketplaceIds;
    private final String includedData;

    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public AmazonSpApiClient(
            @Value("${amazon.enabled:false}") boolean enabled,
            // India is served by the Europe endpoint.
            @Value("${amazon.endpoint:https://sellingpartnerapi-eu.amazon.com}") String endpoint,
            @Value("${amazon.lwa-client-id:}") String clientId,
            @Value("${amazon.lwa-client-secret:}") String clientSecret,
            @Value("${amazon.refresh-token:}") String refreshToken,
            // A21TJRUUN4KGV = Amazon.in
            @Value("${amazon.marketplace-ids:A21TJRUUN4KGV}") String marketplaceIds,
            @Value("${amazon.included-data:BUYER,RECIPIENT,FULFILLMENT,PROCEEDS}") String includedData
    ) {
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.marketplaceIds = List.of(marketplaceIds.split("\\s*,\\s*"));
        this.includedData = includedData;
        this.http = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("user-agent", "ThulirkumVerAdmin/1.0 (Language=Java)")
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConfigured() {
        return enabled && !clientId.isBlank() && !clientSecret.isBlank() && !refreshToken.isBlank();
    }

    /**
     * One page of orders changed since lastUpdatedAfter. Pass the previous page's
     * nextToken to continue; every other value must stay identical between pages.
     */
    public OrdersPage searchOrders(Instant lastUpdatedAfter, String paginationToken) {
        try {
            return fetch(lastUpdatedAfter, paginationToken, includedData);
        } catch (AmazonApiException ex) {
            // Without the restricted PII roles Amazon rejects BUYER/RECIPIENT.
            // Fall back to order data without personal details rather than syncing nothing.
            boolean asksForPii = includedData.contains("BUYER") || includedData.contains("RECIPIENT");
            if ((ex.getStatus() == 400 || ex.getStatus() == 403) && asksForPii) {
                log.warn("Amazon refused buyer/recipient data ({}). Retrying without it. "
                        + "Request the PII roles on your SP-API app to see buyer details.", ex.getMessage());
                return fetch(lastUpdatedAfter, paginationToken, "FULFILLMENT,PROCEEDS");
            }
            throw ex;
        }
    }

    private OrdersPage fetch(Instant lastUpdatedAfter, String paginationToken, String included) {
        String token = accessToken();

        JsonNode body;
        try {
            body = http.get()
                    .uri(uri -> {
                        uri.path("/orders/2026-01-01/orders")
                                .queryParam("lastUpdatedAfter", lastUpdatedAfter.toString())
                                .queryParam("marketplaceIds", String.join(",", marketplaceIds))
                                .queryParam("includedData", included)
                                .queryParam("maxResultsPerPage", 100);
                        if (paginationToken != null && !paginationToken.isBlank()) {
                            uri.queryParam("paginationToken", paginationToken);
                        }
                        return uri.build();
                    })
                    .header("x-amz-access-token", token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String text = new String(res.getBody().readAllBytes());
                        throw new AmazonApiException(res.getStatusCode().value(), abbreviate(text));
                    })
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new AmazonApiException(ex.getStatusCode().value(), abbreviate(ex.getResponseBodyAsString()));
        }

        List<JsonNode> orders = new ArrayList<>();
        if (body != null && body.path("orders").isArray()) {
            body.path("orders").forEach(orders::add);
        }
        String next = body == null ? null : body.path("pagination").path("nextToken").asText(null);
        return new OrdersPage(orders, next == null || next.isBlank() ? null : next);
    }

    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(60))) {
            return accessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            JsonNode response = lwa.post()
                    .uri(LWA_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || response.path("access_token").asText("").isBlank()) {
                throw new AmazonApiException(401, "Amazon did not return an access token");
            }
            accessToken = response.path("access_token").asText();
            accessTokenExpiresAt = Instant.now().plus(Duration.ofSeconds(response.path("expires_in").asLong(3600)));
            return accessToken;
        } catch (RestClientResponseException ex) {
            throw new AmazonApiException(ex.getStatusCode().value(),
                    "Amazon login (LWA) failed: " + abbreviate(ex.getResponseBodyAsString()));
        }
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() > 400 ? text.substring(0, 400) + "..." : text;
    }
}
