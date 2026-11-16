# Phase 8 — Buy X Get Y book bundles (backend)

## What changed

BOGO promotions now live under `offers`, not `home_sections`. The MongoDB `home_sections` BOGO record is **not deleted or overwritten**: it is ignored by the rule engine and omitted from Home Sections API responses. Configure new Buy X Get Y offers in Admin -> Offers.

An `Offer` can be a legacy `COUPON` or a `BUY_GET` rule with:
- `buyBookId`: qualifying paid book
- `getBookId`: free gift book (may be the same book)
- `buyQuantity`: X, between 1 and 50
- `getQuantity`: Y, between 1 and 50
- `active`, `startDate`, `endDate` as for existing offers

The customer only adds **paid copies** of the qualifying book to their cart. For each full group of X paid copies, the backend adds Y gift copies to the checkout and order automatically. For example: Buy 2 Get 1 on 5 purchased units -> 2 free gift units. The gift item is stored with `bogoFreeQuantity == quantity`, and the order's `bogoDiscountPaise` stores its full merchandise value. Cart reservations for paid copies are not deducted twice; gift stock is deducted inside the checkout transaction. Order cancellation returns gift stock.

## API

- `POST /api/admin/offers` with `offerType: BUY_GET`, `buyBookId`, `getBookId`, `buyQuantity`, `getQuantity`, `active` (admin only)
- `GET /api/offers/active` includes published bundle offers
- `POST /api/payments/quote` now returns `freeItems` along with previous totals, including the gift price in `subtotalPaise` and offsetting it in `bogoDiscountPaise`

The Razorpay or wallet charge **excludes** gift value. Coupons apply to paid merchandise; gift units are excluded from cashback. The original payment-intent snapshot, wallet-redemption and settlement flows remain. Existing Phase 6 payment intents with free units already reserved in the cart remain compatible.

## Installation

1. Back up your existing code and Atlas database.
2. Apply the updated backend first and restart it; retain all existing environment secrets, MongoDB URI, and Razorpay configuration.
3. Deploy the Phase 8 frontend.
4. Configure Buy X Get Y rules under Admin -> Offers; previous BOGO home-section selections are preserved in MongoDB but **do not automatically migrate** to the new offers collection.
5. Test payment in Razorpay test mode with no wallet, partial wallet, wallet-only, coupons, same-title gifts, different-title gifts, and cancellation.

## Safety and deployment notes

- Requires a MongoDB replica set supporting transactions, as in Phase 5.
- Customer-visible gift stock is checked at quote generation and again at settlement. Gifts are **not held during an unfinished Razorpay payment**. If they sell out before payment settles, the captured payment is flagged for manual reconciliation/refund; no partial order is committed.
- No Razorpay refund is triggered automatically by cancellation; this is still handled separately.
- No database wipe, book-price migration or bulk modification is performed by this update.
- Java source syntax and isolated rule-engine tests passed. Full Maven, live Atlas and Razorpay tests were **not available here**, so production readiness is **not** verified.
