# Thulirkum Ver — Razorpay Cashback Integration (Backend)

## What changed

The existing book prices and customer data are not altered. The admin's Home Sections
`cashback` configuration (`perBookCashbacks`) controls per-book promotional wallet credit.

- `POST /api/payments/create-order` takes a stable snapshot of the authenticated customer's
  cart, prices, delivery address, coupon totals and per-book cashback, then creates a Razorpay
  order and records a `payment_intents` document. Its response shape remains unchanged.
- `POST /api/payments/verify` validates the signature, confirms the payment's Razorpay order,
  INR currency, **captured** status and exact paise amount. It settles the bookstore order and
  cashback atomically via MongoDB transactions, using the saved checkout snapshot.
- Wallet credits are idempotent and recorded in `wallet_transactions` with a unique reference.
  A duplicate verification does not produce a second order or second cashback credit.
- `GET /api/wallet` exposes the signed-in customer's balance and history.
- Admin cancellation reverses credited cashback within the same MongoDB transaction as the
  order cancellation/stock restoration. **Cancellation does NOT refund the Razorpay payment.**
- `GET /api/admin/payment-intents/review-required` shows captured payments that could not be
  settled because the cart or stock reservations changed before verification.

## Requirements

- Java 21, Spring Boot 3.5.5 and the existing Razorpay library from `pom.xml`.
- MongoDB Atlas replica set or another MongoDB deployment that supports multi-document
  transactions. A standalone MongoDB server is NOT sufficient.
- Existing `application.yml` settings, Razorpay credentials and MongoDB URI. This archive
  does not modify these settings or include your database contents.
- MongoDB account permissions to create indexes. On startup, unique wallet-transaction and
  payment-intent indexes are created. If existing duplicate data blocks an index, startup
  should fail rather than run without duplicate-credit protection.

## Safe installation

1. Back up your current backend **code** and MongoDB data.
2. Merge/copy this `src` directory and `pom.xml` into the existing backend (do not copy
   `.git`, IDE settings, `target` or secrets). The archive includes all previously supplied
   Phase 1, 2 and 3A files, so don't install those ZIPs again after this one.
3. Use your normal environment variables/secrets configuration.
4. Run `mvn clean test` then `mvn spring-boot:run` or deploy via your current pipeline.
5. In a Razorpay **test mode** account, set cashback for one eligible book and a different
   amount for another; verify ₹0 wallet before payment, expected cashback after **captured**
   payment, exactly the same balance after repeated `/verify`, and no credit for failed/COD.
6. Test admin cancellation. The wallet credit must reverse once, without a second reversal.
7. Test a changed cart between `/create-order` and `/verify`; the paid intent must be marked
   `REVIEW_REQUIRED`, without a new order or cashback, for customer support review/refund.

## Limitations / operational safeguards

- The Razorpay payment itself is **not** refunded by a cancellation. Admins must issue/verify
  a real refund in Razorpay as part of their normal refund workflow. Refunds performed only
  in Razorpay's dashboard do **not** currently trigger an automatic wallet reversal; cancel
  or reconcile the corresponding bookstore order.
- Payment reconciliation when the browser never calls `/verify`, automatic Razorpay webhook
  handling, auto-refunds and wallet redemption are **not included**. Do not call this a fully
  automated refunds/reconciliation system.
- There must not be a second independent wallet-credit path elsewhere in your app.
- Cashback is calculated on each purchased book's displayed selling price **before any
  order-level coupon discount**. It is capped at that book's selling price and never reduces
  the Razorpay amount charged.
- Do not deploy untested to production; run integration tests against your own MongoDB Atlas
  test database and Razorpay test mode first.

## Modified source files

- `controller/PaymentController.java`
- `service/OrderService.java`, `service/WalletService.java`
- `model/PaymentIntent.java`, `model/Order.java`
- `repository/PaymentIntentRepository.java`
- `config/WalletTransactionConfig.java` (removes duplicate transaction-manager bean)

## Added source files

- `service/PaymentSettlementService.java`, `service/CashbackRulesService.java`
- `controller/AdminPaymentReviewController.java`
- `config/WalletIndexesConfig.java`
- `src/test/java/com/thulirkumver/bookstore/service/CashbackCalculatorTest.java`
