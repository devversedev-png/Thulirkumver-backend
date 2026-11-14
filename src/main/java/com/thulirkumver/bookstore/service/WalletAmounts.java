package com.thulirkumver.bookstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money calculations for a partial/fully-wallet-funded order; all values are paise. */
public record WalletAmounts(long grossPaise, long redeemedPaise, long razorpayPaise, long cashbackPaise) {
    public static WalletAmounts calculate(long grossPaise, long availablePaise,
                                          boolean useWallet, long eligibleCashbackPaise) {
        if (grossPaise <= 0 || availablePaise < 0 || eligibleCashbackPaise < 0) {
            throw new IllegalArgumentException("Invalid checkout/wallet amount");
        }
        long redeemed = useWallet ? Math.min(grossPaise, availablePaise) : 0L;
        long razorpay = grossPaise - redeemed;
        long cashback = BigDecimal.valueOf(eligibleCashbackPaise)
                .multiply(BigDecimal.valueOf(razorpay))
                .divide(BigDecimal.valueOf(grossPaise), 0, RoundingMode.HALF_UP).longValueExact();
        return new WalletAmounts(grossPaise, redeemed, razorpay, cashback);
    }
}
