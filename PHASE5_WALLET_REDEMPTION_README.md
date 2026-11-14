# Phase 5 — Cashback Wallet Redemption (Spring Boot backend)

This is the **Phase 3 cashback backend** with Phase 5 integrated. Existing MongoDB books, customers, orders, coupons, admin configuration and wallet transactions are **not migrated, overwritten or deleted** by installing this source code. Old records have no wallet-redemption fields and deserialize as 0.

## Checkout behavior

- At checkout the customer may select **Use Wallet** (off by default).
- `POST /api/payments/quote` takes `{ "couponCode": null, "useWallet": true }` and returns all amounts in integer **paise**: `subtotalPaise`, `shippingPaise`, `discountPaise`, `totalPaise`, `walletBalancePaise`, `walletRedeemedPaise`, `razorpayAmountPaise`, `cashbackPaise`, `currency`.
- **Partial wallet**: `POST /api/payments/create-order` with `{ "address": {...}, "couponCode": null, "useWallet": true }` creates a Razorpay order for **only the remaining amount**. The existing signed `/api/payments/verify` verifies Razorpay's captured status, INR, amount, authenticated owner and the saved snapshot. When finalized, a MongoDB transaction writes the order, wallet debit and cashback credit atomically.
- **Entire amount covered by wallet**: `POST /api/payments/wallet-checkout` with the same request completes the order in one MongoDB transaction, without Razorpay. It rejects checkout if the wallet balance is no longer sufficient.
- The storefront order retains its **full gross order total**. `order.walletRedeemedPaise` and `order.razorpayPaidPaise` separately show the funding sources. `amountPaid` reflects the combined paid amount.
- Customer must opt in; a normal Razorpay order does not debit wallet.
- **Cashback applies only to the proportion of an eligible order funded through Razorpay**, to prevent looping rewards from wallet-only orders. The saved cashier/campaign rule snapshot is still used.
- Wallet ledger types: `CASHBACK_CREDIT`, `CASHBACK_REVERSAL`, `WALLET_REDEMPTION`, `WALLET_REDEMPTION_REFUND`. Unique ledger reference keys prevent duplicate entries. There is no endpoint permitting client-controlled balance edits.
- On cancellation, previously redeemed wallet funds are refunded once, and cashback credited for that order is reversed. **No Razorpay refund is initiated by cancellation**; process Razorpay refunds through your existing support/payment review workflow. If cashback has already been spent and a reversal would overdraw the wallet, cancellation may require support intervention; no partial changes are committed.

## Security and payment failure handling

- No wallet funds are debited when a Razorpay order is *created*, when the Checkout popup is closed, or when payment fails. Debiting happens only after the captured payment is verified and the bookstore order is written.
- Database transactions and the conditional wallet-debit update prevent negative balances and double spending. A duplicate successful verification returns the existing order rather than debiting again.
- **Important edge case:** If the customer's wallet is spent in another checkout *after* a partial-wallet Razorpay order is created but *before* its payment is verified, the captured payment cannot be finalized. The intent is marked `REVIEW_REQUIRED`; an administrator must reconcile the capture/refund manually. The code intentionally never creates unbacked store credit or auto-refunds without confirmed provider state.
- On an interrupted wallet-only submission, check My Orders before trying again to avoid unnecessary confusion. A completed first order clears the cart atomically and will not be charged twice by a simple retry.

## Installation

1. Back up the existing repository and database. Use this updated backend source, or apply the backend patch ZIP to your existing Phase 3 backend.
2. Keep all existing environment values and production secrets in your deployment environment; no `.env` secrets are in this ZIP.
3. Configure a **MongoDB replica set/Atlas cluster** supporting transactions and the existing Razorpay key ID/secret. Ensure `WalletMongoTransactionConfig` is enabled.
4. Deploy **backend before frontend**. The frontend uses the new `/api/payments/quote` and `/api/payments/wallet-checkout` routes.
5. Run `mvn test` and test in Razorpay test mode against your real configured MongoDB. Verify partial payment, wallet-only, repeated `/verify`, failed/cancelled Razorpay Checkout, cancellation, stock reservation and refunds before production.
6. **Do not live-deploy based on compilation alone**: live Razorpay and MongoDB integration were not available in this environment.

## Verification performed in preparation

- All production Java source files compiled with Java 21 using locally available Spring/Mongo dependencies and Razorpay SDK *test stand-ins* (not the live SDK).
- Seven pure wallet-amount arithmetic checks passed: partial wallet, full wallet, opt-out, invalid/zero values, no wallet, rounding.
- `WalletAmountsTest.java` included as a JUnit test for your normal `mvn test` run; not executed here with the full Maven/Razorpay test environment.

No databases or existing orders were modified when this package was built.
