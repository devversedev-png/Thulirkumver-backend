package com.thulirkumver.bookstore.integration.msg91;

import com.thulirkumver.bookstore.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP business rules on top of Msg91Client: abuse limits and the short-lived
 * "mobile verified" token used to finish registration.
 *
 * Limits are held in memory, which is fine for a single Render instance. If the
 * backend is ever scaled to several instances, move them to MongoDB or Redis.
 */
@Service
public class OtpService {

    public enum Purpose { REGISTER, LOGIN }

    private static final Duration RESEND_GAP = Duration.ofSeconds(30);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_SENDS_PER_WINDOW = 5;
    private static final Duration VERIFY_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_VERIFY_FAILURES = 5;

    private final Msg91Client msg91;
    private final JwtService jwtService;
    private final boolean requiredForRegistration;

    private final Map<String, Deque<Instant>> sends = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public OtpService(
            Msg91Client msg91,
            JwtService jwtService,
            @Value("${msg91.required-for-registration:true}") boolean requiredForRegistration
    ) {
        this.msg91 = msg91;
        this.jwtService = jwtService;
        this.requiredForRegistration = requiredForRegistration;
    }

    public boolean isEnabled() {
        return msg91.isEnabled();
    }

    /** True when sign-up must carry a verified-mobile token. */
    public boolean isRequiredForRegistration() {
        return msg91.isEnabled() && requiredForRegistration;
    }

    public void send(String mobile10, Purpose purpose) {
        String key = purpose + ":" + mobile10;
        Instant now = Instant.now();

        synchronized (sends) {
            Deque<Instant> history = prune(sends.computeIfAbsent(key, k -> new ArrayDeque<>()), now, SEND_WINDOW);

            if (!history.isEmpty() && Duration.between(history.peekLast(), now).compareTo(RESEND_GAP) < 0) {
                throw new IllegalArgumentException("Please wait 30 seconds before requesting another OTP");
            }
            if (history.size() >= MAX_SENDS_PER_WINDOW) {
                throw new IllegalArgumentException("Too many OTP requests. Please try again in an hour");
            }

            msg91.sendOtp(mobile10);
            history.addLast(now);
        }
    }

    /** Verifies the code with MSG91 and returns a 10-minute token proving the number. */
    public String verifyForRegistration(String mobile10, String otp) {
        checkOtp(mobile10, otp, Purpose.REGISTER);
        return jwtService.createOtpToken(mobile10, Purpose.REGISTER.name());
    }

    /** Verifies the code for login; the caller then loads the account. */
    public void verifyForLogin(String mobile10, String otp) {
        checkOtp(mobile10, otp, Purpose.LOGIN);
    }

    /** Throws unless the token proves ownership of this mobile for sign-up. */
    public void requireRegistrationToken(String otpToken, String mobile10) {
        if (otpToken == null || otpToken.isBlank()) {
            throw new IllegalArgumentException("Please verify your mobile number with the OTP first");
        }
        boolean valid;
        try {
            valid = jwtService.isValidOtpToken(otpToken, mobile10, Purpose.REGISTER.name());
        } catch (Exception ex) {
            valid = false;
        }
        if (!valid) {
            throw new IllegalArgumentException("Mobile verification expired. Please request a new OTP");
        }
    }

    private void checkOtp(String mobile10, String otp, Purpose purpose) {
        if (otp == null || !otp.matches("\\d{4,9}")) {
            throw new IllegalArgumentException("Enter the OTP you received");
        }

        String key = purpose + ":" + mobile10;
        Instant now = Instant.now();

        synchronized (failures) {
            Deque<Instant> history = prune(failures.computeIfAbsent(key, k -> new ArrayDeque<>()), now, VERIFY_WINDOW);
            if (history.size() >= MAX_VERIFY_FAILURES) {
                throw new IllegalArgumentException("Too many incorrect attempts. Request a new OTP in a few minutes");
            }

            if (!msg91.verifyOtp(mobile10, otp)) {
                history.addLast(now);
                throw new IllegalArgumentException("Incorrect or expired OTP");
            }
            failures.remove(key);
        }
    }

    private Deque<Instant> prune(Deque<Instant> history, Instant now, Duration window) {
        while (!history.isEmpty() && Duration.between(history.peekFirst(), now).compareTo(window) > 0) {
            history.pollFirst();
        }
        return history;
    }
}
