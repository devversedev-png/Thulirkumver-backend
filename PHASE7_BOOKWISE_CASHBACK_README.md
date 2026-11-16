# Phase 7: Book-wise Cashback (Backend)

Uses existing HomeSection config `perBookCashbacks` and existing CashbackCalculator / payment snapshot. No schema change and no data migration.

`PUT /api/admin/home-sections/cashback` validates a positive percentage (<=100) or fixed amount (<= selling price) for each admin-selected book and stores only selected rules.

`GET /api/home-sections` now includes `eligibleBookIds` for cashback so Shop/related book cards can show offers even when the home display limit is smaller than the selected set. Public `config.perBookCashbacks` already contains per-book rules.

Existing prices, customers, wallets, order history and data are unchanged. Test with a paid order in Razorpay test mode before deploying to production.
