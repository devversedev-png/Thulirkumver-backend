# Phase 11 — Configurable delivery settings

## Defaults (when MongoDB has no settings document)
- Delivery enabled: true
- Fee: ₹60 (6000 paise)
- Free delivery threshold: ₹499 (49900 paise)
- Free delivery on all orders: false

## Admin controls
Navigate to Admin > Delivery Settings. Save settings to MongoDB collection `store_delivery_settings`, document id `default`.

## Endpoints
- GET /api/delivery-settings (public, shows effective rules)
- GET /api/admin/delivery-settings (admin)
- PUT /api/admin/delivery-settings (admin, JSON fields enabled, feePaise, freeDeliveryThresholdPaise, freeShippingAll)

## Pricing consistency
OrderService uses DeliverySettingsService for all three pricing paths (legacy calculation, checkout quote/payment intent, and offline order creation). Razorpay charge is based on the immutable PaymentIntent snapshot. Cart and Checkout fallback calculations read the public settings endpoint; payment quote remains authoritative. Shipping eligibility is based on merchandise after gift savings and before coupon/wallet redemption. Settings changes only affect new quotes/orders; existing orders and already-created payment intents retain original shipping snapshots.

## Installation
Deploy backend before frontend. Keep your existing secrets and environment settings; these ZIPs do not include live secrets, database data, node_modules, build output, or Git history. No database migration, overwrite or deletion is performed; defaults are read-only until admin saves.

## Verification
- Parsed 51 JS/JSX source files successfully with the TypeScript parser.
- Verified all 3 OrderService shipping calculation points use DeliverySettingsService.
- Checked delivery policy scenarios at ₹450, ₹499, disabled/free shipping, ₹0 fee, empty cart.
- Full Maven/Vite production builds and actual Razorpay/MongoDB integration tests are not available here; run before production.
