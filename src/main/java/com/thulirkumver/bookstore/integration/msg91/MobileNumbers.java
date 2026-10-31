package com.thulirkumver.bookstore.integration.msg91;

import java.util.List;

/**
 * Indian mobile number helpers. Numbers are stored as the bare 10-digit
 * national number and sent to MSG91 with the 91 country code.
 */
public final class MobileNumbers {

    private MobileNumbers() {
    }

    /** Returns the 10-digit national number, or throws IllegalArgumentException. */
    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");

        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        if (!digits.matches("[6-9]\\d{9}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number");
        }
        return digits;
    }

    /** MSG91 wants the number with country code and no plus sign. */
    public static String forMsg91(String normalized) {
        return "91" + normalized;
    }

    /** Every format an existing user's mobile might have been saved in. */
    public static List<String> storedVariants(String normalized) {
        return List.of(
                normalized,
                "91" + normalized,
                "+91" + normalized,
                "+91 " + normalized,
                "0" + normalized
        );
    }
}
