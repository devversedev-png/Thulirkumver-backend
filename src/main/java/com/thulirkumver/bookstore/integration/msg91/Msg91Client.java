package com.thulirkumver.bookstore.integration.msg91;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the MSG91 OTP API (v5).
 *
 * MSG91 generates, delivers and verifies the OTP itself, so nothing about the
 * code is stored on our side.
 *
 * Gotcha: the /api/v5/otp endpoints can answer HTTP 200 with
 * {"type":"error"} (for example an invalid authkey), so the JSON "type"
 * field is checked, not just the status code.
 */
@Component
public class Msg91Client {

    private static final Logger log = LoggerFactory.getLogger(Msg91Client.class);

    private final RestClient http;
    private final boolean enabled;
    private final String authKey;
    private final String templateId;
    private final int otpLength;
    private final int otpExpiryMinutes;

    public Msg91Client(
            @Value("${msg91.enabled:false}") boolean enabled,
            @Value("${msg91.base-url:https://control.msg91.com}") String baseUrl,
            @Value("${msg91.auth-key:}") String authKey,
            @Value("${msg91.template-id:}") String templateId,
            @Value("${msg91.otp-length:6}") int otpLength,
            @Value("${msg91.otp-expiry-minutes:10}") int otpExpiryMinutes
    ) {
        this.enabled = enabled;
        this.authKey = authKey;
        this.templateId = templateId;
        this.otpLength = otpLength;
        this.otpExpiryMinutes = otpExpiryMinutes;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("authkey", authKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Sends a fresh OTP to the 10-digit mobile number. */
    public void sendOtp(String mobile10) {
        requireConfigured();

        JsonNode body = http.post()
                .uri(uri -> uri.path("/api/v5/otp")
                        .queryParam("template_id", templateId)
                        .queryParam("mobile", MobileNumbers.forMsg91(mobile10))
                        .queryParam("otp_length", otpLength)
                        .queryParam("otp_expiry", otpExpiryMinutes)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(JsonNode.class);

        if (!isSuccess(body)) {
            log.warn("MSG91 send failed for ***{}: {}", mobile10.substring(6), body);
            throw new IllegalStateException("Could not send the OTP right now. Please try again shortly.");
        }
    }

    /** Returns true only when MSG91 confirms the OTP is correct and unexpired. */
    public boolean verifyOtp(String mobile10, String otp) {
        requireConfigured();

        JsonNode body = http.get()
                .uri(uri -> uri.path("/api/v5/otp/verify")
                        .queryParam("mobile", MobileNumbers.forMsg91(mobile10))
                        .queryParam("otp", otp)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        boolean ok = isSuccess(body);
        if (!ok) {
            log.info("MSG91 verify rejected for ***{}: {}", mobile10.substring(6), body);
        }
        return ok;
    }

    private boolean isSuccess(JsonNode body) {
        return body != null && "success".equalsIgnoreCase(body.path("type").asText(""));
    }

    private void requireConfigured() {
        if (!enabled) {
            throw new IllegalStateException("OTP login is not enabled");
        }
        if (authKey.isBlank() || templateId.isBlank()) {
            throw new IllegalStateException("MSG91_AUTH_KEY and MSG91_TEMPLATE_ID must be set");
        }
    }
}
