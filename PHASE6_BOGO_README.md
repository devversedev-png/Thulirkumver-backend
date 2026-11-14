# Phase 6 — Buy 1 Get 1 Free (same title)

This is the complete Phase 5 Spring Boot backend PLUS Phase 6 BOGO, not a replacement for earlier wallet/cashback features.

## Rules

- Admin manages eligible titles in **Admin Dashboard → Home Sections → Buy 1 Get 1 Free** (`key=bogo`). Select books, toggle Enable, then Save section. Nothing is eligible until admin selects it.
- For each eligible **same book/title**, 2 units = 1 paid + 1 free, 3 = 2 paid + 1 free, 4 = 2 paid + 2 free. Distinct books are **not** paired. One unit of an eligible book is still full price.
- Discounts are automatic, not coupons; do not overwrite `Book.price`, `basePrice`, original price, or any existing customer/book records.
- All units, paid and free, are included in order lines and must be in stock. A free unit is not an extra invisible order line: the shopper chooses the total quantity including free copies.
- BOGO is applied to merchandise first, shipping is determined from the **post-BOGO merchandise due** (shipping free from ₹499), then an optional coupon discounts the remaining merchandise, then optional wallet funds reduce amount charged to Razorpay.
- Existing coupon codes can be used. Their percentage and minimum order are calculated against post-BOGO merchandise (so discounts do not apply to already-free copies).
- Cashback is earned only on paid book-unit quantities, then proportionally reduced for wallet-funded portions of the checkout. Fully wallet-funded orders earn no cashback.
- A payment intent holds the BOGO quote, free quantities, wallet redemption and final Razorpay charge; admin configuration changes after initiation do not rewrite the captured order.
- Cancellation restores the full shipped/reserved order quantity (including free units). Existing wallet/cashback reversal logic is unchanged.

## Files

- New `service/BogoCalculator.java` + `service/BogoRulesService.java`.
- Updated `controller/HomeContentController.java` to add the BOGO admin/home section. Saving a BOGO section does not trigger legacy book-pricing migrations.
- Updated `service/OrderService.java` for standard and Razorpay/wallet checkout; snapshots use BOGO quantities for cashback.
- Updated `model/OrderItem.java`, `model/PaymentIntent.java`, `model/Order.java` with additive `bogoFreeQuantity`/`bogoDiscountPaise` fields; old records safely default to zero.
- Updated quote DTO/controller to expose `bogoDiscountPaise`.
- New `BogoCalculatorTest.java` unit tests.

## How to enable

1. Back up project + Atlas database before deployment.
2. Deploy **updated Phase 6 backend first**, keeping existing .env/secrets and database.
3. Deploy Phase 6 frontend. Do not deploy old frontend over it.
4. In admin Dashboard > Home Sections > Buy 1 Get 1 Free, select eligible books, leave Enable checked, click Save section.
5. In customer Shop/Book Details, find selected books, add 2 copies, then review cart/checkout. No extra free copy is added automatically; quantity 2 includes the paid + free unit.
6. Test with Razorpay test credentials, partial wallet, wallet-only, coupon, duplicate payment callback, cancellation, and out-of-stock behavior before production.

## Limitations / testing

- Same-title BOGO only; mixed-title BOGO / choice of different free book is not included.
- Backend Java sources compiled offline against local Spring dependencies with temporary Razorpay SDK shims; pure BOGO checkout-math smoke tests passed. JUnit test source is included, but full Maven/Atlas/Razorpay integration tests were not run.
- Checkout labels were JSX-syntax validated with TypeScript's JSX parser. A production Vite build wasn't possible because the previously downloaded node_modules lacks the Vite package and registry wasn't reachable.
- Does not modify actual database contents; applying patches adds code. New BOGO settings record is only created when the application's admin home-sections initialization runs; book values are not edited by BOGO.
- Existing Phase 5 payment/retry/refund caveats still apply: Razorpay gateway refunds are not automatic, and manual review may be needed after a successfully captured payment that cannot be finalized.
