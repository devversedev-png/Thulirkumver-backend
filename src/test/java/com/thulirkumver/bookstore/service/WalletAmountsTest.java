package com.thulirkumver.bookstore.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalletAmountsTest {
    @Test void partialRedemptionReducesRazorpayAndCashback() {
        WalletAmounts result = WalletAmounts.calculate(50000, 15000, true, 5000);
        assertEquals(15000, result.redeemedPaise());
        assertEquals(35000, result.razorpayPaise());
        assertEquals(3500, result.cashbackPaise());
    }

    @Test void walletOnlyCheckoutEarnsNoCashback() {
        WalletAmounts result = WalletAmounts.calculate(50000, 100000, true, 5000);
        assertEquals(50000, result.redeemedPaise());
        assertEquals(0, result.razorpayPaise());
        assertEquals(0, result.cashbackPaise());
    }

    @Test void notSelectedPreservesOriginalCheckout() {
        WalletAmounts result = WalletAmounts.calculate(50000, 15000, false, 5000);
        assertEquals(0, result.redeemedPaise());
        assertEquals(50000, result.razorpayPaise());
        assertEquals(5000, result.cashbackPaise());
    }

    @Test void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> WalletAmounts.calculate(0, 100, true, 0));
        assertThrows(IllegalArgumentException.class, () -> WalletAmounts.calculate(50, -1, true, 0));
    }
}
