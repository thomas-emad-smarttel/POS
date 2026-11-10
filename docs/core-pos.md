# Core POS Guide (Phase 4)

## Overview

Phase 4 implements the end-to-end restaurant ordering workflow across all modules:

- **Menu Management** (`GET/POST/PUT /api/v1/menu/**`): Taxes (inclusive and exclusive), categories with branch-level visibility/display overrides, products with variants, required/min/max modifier groups, barcode/SKU lookup, and branch-level price/availability/tax overrides.
- **Dining Areas & Concurrency-Safe Tables** (`GET/POST/PATCH /api/v1/tables/**`): Optimistically versioned table assignments (`version`) preventing two terminals from claiming the same `AVAILABLE` table simultaneously.
- **Orders & Authoritative Server-Side Pricing** (`GET/POST/PUT/DELETE /api/v1/orders/**`):
  - Clients submit only product/variant/modifier IDs, quantities, and notes — never authoritative unit prices or order totals.
  - `PricingCalculator` uses exact `BigDecimal` arithmetic (`Money` scale 2, `RoundingMode.HALF_UP`) to compute line gross totals, item-level discounts, pro-rated order-level discounts, and inclusive or exclusive tax summaries.
  - Historical snapshots (`product_name_snapshot`, `variant_name_snapshot`, `unit_price_snapshot`, `tax_name_snapshot`, `tax_rate_snapshot`, modifier name/delta snapshots) isolate existing checks from later catalog edits.
  - Discounts exceeding a user's `max_discount_pct` role threshold require `orders.discount.approve` or a `supervisorApprovalId`.
  - Supports **Hold / Retrieve**, **Transfer Table**, **Split Order**, **Merge Orders**, **Change Order Type**, **Send to Kitchen** (writes transactional `outbox_events` and locks sent items against quantity/modifier edits), and **Cancel / Reopen**.

## Multi-Terminal Realtime Synchronization & Offline Cache

- `WebSocketConfig` authenticates WebSocket upgrades on `/ws/events` using `Authorization: Bearer <token>` and binds the session to its tenant and active branch.
- `WebSocketBranchEventHub` broadcasts compact `BranchRealtimeEventDto` messages with per-branch monotonic `sequenceNumber` values and maintains a bounded catch-up ring buffer served via `GET /api/v1/events/recent?sinceSequence=...`.
- On reconnect or event receipt, desktop terminals re-fetch authoritative tables and orders from the Local Server REST API.
- **Offline Read-Only Cache**: `LocalCacheDatabase` caches categories (`cached_categories`) and branch products (`cached_products`) in SQLite WAL so terminals can display a read-only offline menu badge when the Local Server is unreachable. Order creation and mutations are explicitly disabled offline rather than queuing divergent state.
