package com.thulirkumver.bookstore.model;

/**
 * Pure pricing arithmetic (no Spring, no database) so it can be reasoned about and tested in isolation.
 *
 * Rule: a promotion is always calculated from the book's normal selling price (Book.basePrice),
 * never from a price that has already been discounted.
 */
public final class BookPricing {
    private static final double EPS = 0.011;

    private BookPricing() {
    }

    /** An admin-configured promotion for one book. */
    public record Promo(String type, double value) {
    }

    public static boolean isValidType(String type) {
        return "PERCENT".equals(type) || "FIXED".equals(type);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static boolean close(double a, double b) {
        return Math.abs(a - b) <= EPS;
    }

    /** Price after applying a promotion to a normal selling price. */
    public static double discounted(double base, String type, double value) {
        double result = "FIXED".equals(type) ? base - value : base * (1 - value / 100.0);
        return round2(Math.max(0, result));
    }

    public static double discounted(double base, Promo promo) {
        return discounted(base, promo.type(), promo.value());
    }

    /**
     * Works out the normal selling price of a record saved by the old logic, which overwrote
     * {@code price} with the discounted amount and calculated from {@code originalPrice}.
     *
     * <ul>
     *   <li>No promotion configured: the stored price is the normal price.</li>
     *   <li>The stored price equals the promotion applied once (or repeatedly, i.e. compounded) to
     *       {@code originalPrice}: it was produced by the old logic, so {@code originalPrice} is the base.</li>
     *   <li>Anything else: the stored price is not a product of the promotion, so it is the normal price
     *       (this preserves legitimate prices that sit below MRP).</li>
     * </ul>
     */
    public static double legacyBase(double price, double originalPrice, Promo promo) {
        if (promo == null || originalPrice <= 0) return price;
        double step = originalPrice;
        for (int i = 0; i < 4; i++) {
            step = discounted(step, promo);
            if (close(step, price)) return originalPrice;
        }
        return price;
    }
}
