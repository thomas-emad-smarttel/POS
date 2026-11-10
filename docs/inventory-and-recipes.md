# Phase 7 — Inventory Management, Versioned Recipes, Ingredient Consumption & Stock Control

## Overview

Phase 7 extends the modular monolith with full restaurant inventory control, unit conversions, immutable versioned recipes with sub-recipes and modifier rules, event-driven ingredient reservations/consumption, append-only stock movement ledgers, Weighted Average Cost (WAC) valuation, waste capture, physical stock counts, inter-branch transfers, deduplicated alerts, and programmatic JavaFX inventory screens.

---

## Database Schema (`V007` + `V013`)

`V013__inventory_recipes_consumption_counts_transfers.sql` builds incrementally on `V007__inventory_and_purchasing.sql` while preserving existing tables and the `trg_stock_movements_immutable` trigger:

- **`inventory_categories`, `unit_conversions`, `inventory_policies`**: Tenant categories, custom item/tenant unit conversion graphs, and branch-level stock commitment/threshold policies.
- **Extended `ingredients` & `stock_locations`**: Supports item types (`RAW_INGREDIENT`, `PREPARED_INGREDIENT`, `SEMI_FINISHED`, `FINISHED_GOOD`, `PACKAGING`, `CONSUMABLE`, `OTHER`), storage types, minimum/reorder thresholds, batch/expiry flags, and item-level negative-stock overrides (`INHERIT | DENY | ALLOW | SUPERVISOR_OVERRIDE`).
- **Versioned Recipes (`recipes`, `recipe_versions`, `recipe_version_ingredients`, `recipe_modifier_mappings`)**: Supports multi-version product, variant, branch-specific, and sub-recipe BOMs plus modifier actions (`ADD`, `REMOVE`, `MULTIPLY`, `REPLACE`). Historical published versions are preserved untouched (`ARCHIVED`) when new versions are published.
- **Immutable Ledger & Balances (`stock_movements`, `stock_balances`)**: Tracks `quantity_on_hand`, `quantity_reserved`, derived `available = on_hand - reserved`, `average_unit_cost`, original input units/quantities, before/after snapshots, cost totals, and idempotency keys protected by `trg_stock_movements_immutable`.
- **Order/Kitchen Consumption (`stock_reservations`, `stock_reservation_items`, `inventory_processed_events`)**: Binds each `(order_item_id, inventory_item_id)` to the active `recipe_version_id` resolved at dispatch time and tracks processed outbox events idempotently.
- **Operations (`stock_waste_records`, `stock_counts`, `stock_count_items`, `stock_transfers`, `stock_transfer_items`, `inventory_alerts`)**: Full auditability without `CASCADE` deletes on historical records, plus `uq_inventory_alerts_active_dedup` preventing duplicate active alerts.

---

## Units, Precision & Recipe Versioning

- `UnitConversionService` performs `BigDecimal` conversions (`RoundingMode.HALF_UP`, intermediate scale 6, ledger scale 4, minimum non-zero retention `0.0001`), resolving item-specific package overrides (e.g., `1 BOX = 24 PCS` for Cheddar) before tenant/dimensional factors, and rejects incompatible dimensions (`WEIGHT` vs `VOLUME` without custom conversion).
- `RecipeCalculationService` prevents circular sub-recipe cycles (`CIRCULAR_SUB_RECIPE`) at draft/publish time, expands nested sub-recipes, applies ingredient waste % and version prep loss %, and processes modifier selections.

---

## Reservation-to-Consumption Policy (Exact Transition Rules)

Controlled per branch by `inventory_policies.commitment_mode` (`InventoryOutboxConsumerService` + `StockLedgerService`):

1. **Send to Kitchen (`ORDER_SENT_TO_KITCHEN` -> `INVENTORY_RESERVE_ORDER`)**:
   - Under default `RESERVE_ON_SEND_CONSUME_ON_PREP`, reserves expanded recipe ingredients using the active recipe version frozen onto `stock_reservation_items` (`quantity_reserved` increases, `Available` decreases).
   - Under `DIRECT_CONSUME_ON_SEND`, posts `SALE_CONSUMPTION` immediately.
   - Tracks individual `order_item_id`s so supplemental kitchen dispatches never duplicate already-reserved items.
2. **Kitchen Prep Start (`KitchenWorkflowService.startTicket` / `PREPRARING` -> `INVENTORY_CONSUME_PREP`)**:
   - **Exact point reservation becomes consumption**: Converts `RESERVED` lines into `SALE_CONSUMPTION` movements using the frozen `recipe_version_id` snapshot, atomically reducing both `quantity_on_hand` and `quantity_reserved`.
3. **Cancellation Before vs After Consumption**:
   - Cancelling an order before prep (`INVENTORY_RELEASE_ORDER`) releases remaining `RESERVED` lines via `RESERVATION_RELEASE`.
   - Cancelling a kitchen item after preparation started or issuing a **customer payment refund** (`RefundService`) **never** automatically restocks consumed ingredients; staff must record explicit Waste or authorized Return/Reversal movements.
4. **Re-fire**: `KitchenWorkflowService.refireTicket` emits `INVENTORY_REFIRE_TICKET`, posting a distinct idempotent consumption batch keyed to `refireTicketId`.

---

## Weighted Average Costing, Waste, Counts, Transfers & Alerts

- **Costing (`CostingService`)**: Incoming receipts and positive adjustments update `average_unit_cost` using Weighted Average Cost (`(prevQty * prevCost + inQty * inCost) / (prevQty + inQty)`). Outgoing consumption/waste/transfers snapshot the current cost onto `stock_movements` without rewriting historical costs.
- **Waste (`StockLedgerService.recordWaste`)**: Appends `WASTE` movements, checks `waste_approval_threshold_amount` for supervisor approval, enforces `idempotencyKey`, and raises deduplicated `HIGH_WASTE` alerts.
- **Physical Counts (`StockCountService`)**: `DRAFT -> SUBMITTED -> APPROVED (or RECOUNT_REQUESTED) -> POSTED`. Prevents double-posting (`ALREADY_POSTED`), preserves `original_counted_quantity`, enforces variance % approval thresholds, and posts variance deltas solely via `STOCK_ADJUSTMENT_IN` / `STOCK_ADJUSTMENT_OUT` movements.
- **Branch Transfers (`StockTransferService`)**: `REQUESTED -> APPROVED -> DISPATCHED -> PARTIALLY_RECEIVED / RECEIVED` within tenant boundaries, enforcing source/destination branch permissions, preventing over-receipt, recording damaged/missing lines, and propagating unit cost into destination WAC.
- **Alerts (`inventory_alerts`)**: Deduplicated per `(tenant_id, branch_id, dedup_key)` across `LOW_STOCK`, `OUT_OF_STOCK`, `HIGH_WASTE`, `UNUSUAL_VARIANCE`, with acknowledge/resolve API and WebSocket notices.

---

## RBAC & API Summary

Granular permissions under `PermissionCode` (`inventory.view`, `inventory.item.manage`, `inventory.location.manage`, `inventory.recipe.view`, `inventory.recipe.manage`, `inventory.cost.view`, `inventory.stock.adjust`, `inventory.stock.waste`, `inventory.stock.count`, `inventory.stock.count.approve`, `inventory.stock.transfer`, `inventory.stock.transfer.approve`, `inventory.stock.receive`, `inventory.alert.manage`, `inventory.report.view`). `KITCHEN_STAFF` have zero inventory adjustment or costing access; cost fields are omitted from DTOs unless the caller holds `inventory.cost.view`. All REST endpoints live under `/api/v1/inventory/**` (`InventoryController`).

---

## Known Limitations & Future Phases

- **Purchasing & Supplier POs**: Movement types (`PURCHASE_RECEIPT`, `SUPPLIER_RETURN`) and schema hooks are in place, while supplier ordering and GRN receiving workflows are intentionally deferred to Phase 8 (supplier purchasing).
- **Expiry Batch FIFO/FEFO Consumption**: Expiry/batch fields (`expiry_tracking`, `batch_tracking`, `batch_number`, `expiry_date`) and alert types (`EXPIRING_SOON`, `EXPIRED`) are captured on items and movements, while automatic batch-level lot FIFO/FEFO selection and scheduled expiry scanning are not implemented in Phase 7.
- **JavaFX Interactive Verification**: All JavaFX screens (`InventoryDashboardView`, `InventoryCatalogView`, `RecipeEditorView`, `StockOperationsView`) compile cleanly and bind via `LocalServerClient` (verified with HTTP round-trip tests), while live GUI click-through was not executed in an interactive desktop display session.
