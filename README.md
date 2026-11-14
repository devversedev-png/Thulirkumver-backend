# Wallet Backend Phase 2 — Wallet API and atomic ledger

Copy `src/main/java/...` into the corresponding backend package. Requires Phase 1 files.

- `GET /api/wallet` returns `balancePaise`, `currency`, and `transactions` for the authenticated customer.
- `WalletService.creditCashback(userId, orderId, cashbackPaise)` is an internal method; it must ONLY be called by a verified, captured Razorpay payment completion flow, never from a customer request.
- This phase does NOT yet hook into Razorpay; it will not award any cashback on its own.
- Requires MongoDB Atlas replica set and transactions enabled. Ensure `wallet_transactions.referenceKey` has a unique index and `wallets.userId` has a unique index before enabling crediting. Review existing indexes and data before applying any changes.
- If your security configuration blocks `/api/wallet`, allow it for authenticated customers only. Do not allow anonymous requests.
- The service assumes `Authentication.getName()` is an email, as the existing OrderController does. Verify your authentication principal uses the email before deployment.
- Do not use this package with standalone MongoDB that does not support multi-document transactions.
- Test on a staging database first. Concurrent credit requests should result in one transaction and one balance increase.

Next phase: integrate payment-intent snapshots, Razorpay verification, order persistence, cashback calculation from HomeSection, and refund reversal. No production payment flow has been modified yet.
