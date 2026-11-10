# Phase 6 — Kitchen Display System (KDS), Station Routing & Kitchen Workflow

## Overview

Phase 6 extends the branch Local Server and JavaFX POS Desktop client with a multi-station Kitchen Display System (KDS), deterministic station routing, Expo/Expediter coordination, reliable idempotent outbox consumption, real-time WebSocket updates, and non-blocking ESC/POS kitchen ticket printing.

---

## Database Schema (`V006` + `V012`)

`V012__kitchen_display_routing_and_printers.sql` builds incrementally on `V006__kitchen_display.sql` without altering prior migrations or the immutable `kitchen_ticket_events` trigger (`trg_kitchen_ticket_events_immutable`):

- **`kitchen_stations`**: Extended with `expected_prep_minutes`, `expo_enabled`, `paper_width` (`32..64`), `receipt_language` (`EN`, `AR`, `BILINGUAL`), `receipt_encoding` (`UTF-8`, `CP864`, `WPC1256`), `default_station`, and optimistic-locking `version`.
- **`kitchen_station_assignments`**: Maps terminal/user assignments per station (`PRIMARY` / `SECONDARY`).
- **`kitchen_routing_rules`**: Priority-ordered branch rules supporting `product_id`, `category_id`, or `BRANCH_DEFAULT` (both `null`), plus optional `expected_prep_minutes_override`.
- **`kitchen_display_configs`**: Branch-level KDS & Expo defaults (`expo_enabled`, `default_sort_mode`, `default_expected_prep_minutes`, `auto_print_on_dispatch`).
- **`kitchen_tickets` / `kitchen_ticket_items`**: Extended with `order_number_snapshot`, `order_type_snapshot`, `table_label_snapshot`, `waiter_id`, `waiter_name_snapshot`, `source_outbox_event_id`, `ticket_kind` (`INITIAL`, `SUPPLEMENTAL`, `REFIRE`), `expected_prep_minutes`, and optimistic `version` columns.
- **`kitchen_processed_events`**: Deduplication table keyed by `outbox_event_id` for idempotent consumption.
- **`kitchen_unrouted_items`**: Explicit exception queue (`OPEN` / `RESOLVED`) capturing order items that match no active station route.
- **`kitchen_print_jobs`**: Tracks per-ticket print attempts (`PRINTED`, `PRINTER_UNAVAILABLE`, `FAILED`) keyed by `(tenant_id, branch_id, idempotency_key)` for duplicate-safe retries.

---

## Station Routing Engine

Implemented in pure domain logic (`KitchenRoutingEngine`):

1. **Product-level rules** (`product_id` matches): highest precedence. Supports multi-station fan-out when multiple active stations match the product.
2. **Category-level rules** (`category_id` matches when no product rule applies).
3. **Branch-default rules** (`product_id IS NULL AND category_id IS NULL`) or stations flagged `default_station = TRUE`.
4. **Unrouted Items**: If no active station resolves, the item is recorded in `kitchen_unrouted_items` (`status = OPEN`) so kitchen managers can inspect and route it manually via `POST /api/v1/kitchen/unrouted-items/{id}/resolve` (optionally saving a reusable routing rule).

---

## Outbox Processing & Supplemental Dispatches

- When POS staff click **Send to Kitchen** (`POST /api/v1/orders/{orderId}/send-to-kitchen`), `OutboxKitchenDispatchAdapter` atomically writes an `ORDER_SENT_TO_KITCHEN` outbox record containing a complete immutable item/modifier/prep-note snapshot (`KitchenPayloadCodecAdapter`).
- Cancelling a dispatched order writes `ORDER_CANCELLED_IN_KITCHEN` so active non-terminal tickets for that order are automatically cancelled with reason logging.
- `KitchenOutboxScheduler` polls `outbox_events` (`ORDER_SENT_TO_KITCHEN`, `ORDER_CANCELLED_IN_KITCHEN`) with PostgreSQL `FOR UPDATE SKIP LOCKED` (`claimPendingKitchenOutboxEvents`).
- Duplicate delivery checks both `kitchen_processed_events` and previously dispatched `order_item_id`s:
  - First dispatch creates `INITIAL` tickets per station.
  - Later additions (`addItem` -> second `send-to-kitchen`) create `SUPPLEMENTAL` tickets containing **only newly added items**.
- Transient failures increment `retry_count` with bounded backoff and move items to `DEAD_LETTER` after 5 failed attempts.

---

## Ticket & Item Lifecycle, Overdue Tracking & Expo

`KitchenTicketStatePolicy` enforces valid transitions and optimistic locking (`version`):

- **Ticket lifecycle**: `QUEUED` -> `ACCEPTED` -> `PREPARING` -> `READY` -> `SERVED` / `BUMPED`, plus `ON_HOLD` (pause/release) and `CANCELLED` (requires reason).
- **Item-level partial readiness**: Updating individual items to `READY` keeps the parent ticket in `PREPARING` until every non-cancelled item satisfies readiness, at which point the ticket automatically transitions to `READY`.
- **Re-fire**: `POST /api/v1/kitchen/tickets/{id}/refire` creates a new `URGENT` `REFIRE` ticket preserving the original ticket and its immutable `kitchen_ticket_events` history, and triggers a `REFIRE` kitchen print job.
- **Overdue computation**: Tickets exceeding `expected_prep_minutes` since `fired_at` (while not `READY`, `ON_HOLD`, `SERVED`, `BUMPED`, or `CANCELLED`) are flagged overdue.
- **Expo coordination**: `GET /api/v1/kitchen/expo` aggregates tickets per order across all stations; `POST /api/v1/kitchen/expo/{orderId}/serve` rejects partial completion (`422 EXPO_ORDER_NOT_READY`) until every non-cancelled station ticket is `READY` or `SERVED`.

---

## ESC/POS Kitchen Printing & Resilience

- `EscPosKitchenTicketFormatter` renders configurable 32–64 column ESC/POS kitchen tickets in `EN`, `AR`, or `BILINGUAL` layouts with explicit banners (`INITIAL`, `SUPPLEMENTAL`, `REFIRE`, `CANCELLED`).
- `HardwareAndTerminalGatewayConfig` isolates the receipt printer (`@Primary`) from the dedicated `@Qualifier("kitchenPrinter")` bean (`VirtualReceiptPrinter("Virtual-Kitchen-Printer-80mm")` in hardware-free environments).
- Printer outages (`PRINTER_UNAVAILABLE`) never roll back ticket creation or outbox completion; operators inspect failed jobs (`GET /api/v1/kitchen/print-jobs/failed`) and retry cleanly (`POST /api/v1/kitchen/print-jobs/{jobId}/retry`).

---

## Security & RBAC Permissions

Kitchen permissions added in `PermissionCode` and seeded across default roles (`DefaultRoleTemplate`):

| Permission | Description |
|---|---|
| `kitchen.view` | View kitchen stations, tickets, and print jobs |
| `kitchen.expo.view` | View Expo multi-station order overview |
| `kitchen.station.manage` | Create/update kitchen stations & printer settings |
| `kitchen.routing.manage` | Manage routing rules, display defaults, & resolve unrouted items |
| `kitchen.ticket.accept` | Accept queued tickets |
| `kitchen.ticket.start` | Start prep / update item statuses |
| `kitchen.ticket.ready` | Mark items/tickets ready |
| `kitchen.ticket.serve` | Serve/bump ready tickets & Expo orders |
| `kitchen.ticket.hold` | Hold and release tickets |
| `kitchen.ticket.cancel` | Cancel kitchen tickets with reason |
| `kitchen.ticket.refire` | Issue high-priority `REFIRE` tickets |
| `kitchen.printer.manage` | Retry failed kitchen print jobs |

`KITCHEN_STAFF` and `CHEF` roles receive **zero financial/payment/cash/refund permissions**.

---

## Desktop KDS & Expo Views & Real-Time Updates

- **Kitchen Display (`KitchenDisplayView`)**: Station selector, sort mode (`FIFO`, `PRIORITY`, `OVERDUE_FIRST`), queue board with elapsed timer & overdue highlights, per-item Ready buttons, ticket Accept/Start/Ready/Serve/Hold/Cancel/Refire controls, failed print retry & unrouted item banners.
- **Kitchen Expo (`KitchenExpoView`)**: Cross-station readiness cards per order showing pending stations and gating **Serve Entire Order** until all stations are ready.
- **Realtime**: Both KDS and Expo subscribe to `WebSocketBranchEventHub` (`/ws/events`, catch-up via `/api/v1/events/recent`) for `KITCHEN_TICKET_*` and `KITCHEN_UNROUTED_ITEM_*` events.

---

## Known Boundaries & Verification Scope

- Printer output in `pos-local-server` and `pos-tests` targets `VirtualReceiptPrinter` (`pos-device-integration`); live physical USB/network ESC/POS kitchen printers require implementing a hardware `ReceiptPrinter` bean and were not tested against physical devices.
- PostgreSQL integration suites run against Docker PostgreSQL 16 when Docker is active, or automatically fall back to real PostgreSQL 16 via EmbeddedPostgres when Docker is unavailable.
