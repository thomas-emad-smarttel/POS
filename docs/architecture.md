# System Architecture — Restaurant POS Platform

## Module Responsibilities & Dependency Rules

| Module | Responsibility | Allowed Internal Dependencies |
|---|---|---|
| `pos-shared` | Value objects (`Money`, `TenantId`, `BranchId`, `CorrelationId`), `PermissionCode`, `AuditAction`, API DTOs | None |
| `pos-domain` | Pure domain models, exceptions, outbound ports (`AuditLogPort`, `SystemClockPort`) | `pos-shared` |
| `pos-application` | Use cases, RBAC `PermissionPolicy`, `AuditRecordingService`, `BoundedExecutors` | `pos-domain`, `pos-shared` |
| `pos-infrastructure` | PostgreSQL Flyway migrations (`V001`), crypto (`BCryptPasswordEncoder`), audit/clock adapters | `pos-domain`, `pos-application`, `pos-shared` |
| `pos-device-integration` | Hardware Abstraction Layer (`ReceiptPrinter`, `CashDrawer`, `BarcodeScanner`, `WeighingScale`) + virtual adapters | `pos-shared` |
| `pos-api` | REST controllers, Spring Security filter chain, `CorrelationIdFilter`, `GlobalExceptionHandler` | `pos-application`, `pos-shared`, `pos-device-integration` |
| `pos-local-server` | Branch Spring Boot executable server | `pos-api`, `pos-infrastructure` |
| `pos-cloud` | Central multi-tenant Cloud Spring Boot executable server | `pos-application`, `pos-infrastructure`, `pos-shared` |
| `pos-desktop` | Programmatic JavaFX terminal UI, CSS Design Tokens, SQLite WAL cache, async `LocalServerClient` | `pos-shared`, `pos-device-integration` |
| `pos-tests` | ArchUnit architectural governance tests & cross-module verification | All modules (`test` scope) |

All boundaries above are verified automatically by `ArchitectureBoundaryTest` in `pos-tests`.

## Initial Security & RBAC Foundation

- **Permission Catalog:** Dot-delimited action codes in `PermissionCode` (`orders.create`, `payments.refund`, `cash.drawer.open`, etc.).
- **Schema:** `V001`–`V009` Flyway migrations in `pos-infrastructure` establish the full PostgreSQL schema across tenancy (`tenants`, `branches`, `terminals`, `server_nodes`), RBAC/audit/approvals (`users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `user_branch_access`, `user_role_assignments`, `user_sessions`, `supervisor_approvals`, `audit_logs`), menu/taxes, dining tables & orders (with historical snapshots), append-only payments & cash sessions, kitchen display, inventory stock ledger & purchasing, offline sync (outbox/inbox/cursors/conflicts/dead-letters), and hardware devices.
- **Immutability & Isolation:** Append-only financial, inventory, kitchen event, status history, and audit ledgers are protected by the PostgreSQL `prevent_append_only_mutation()` trigger. JPA entities stay confined inside `pos-infrastructure` (never leaked to `pos-domain` or API responses) and `DatabaseExceptionTranslator` maps SQL constraint/optimistic-lock errors into domain exceptions.
- **Audit Persistence:** `JpaAuditLogAdapter` persists sanitized audit entries to PostgreSQL `audit_logs` (with `AuditPayloadSanitizer` redacting secrets/PAN/CVV), while `StructuredAuditLogAdapter` remains a no-DB fallback when `pos.persistence.enabled=false` (`dev` profile).
- **API Safety:** `ApiSecurityConfig` enforces deny-by-default authentication on `/api/**` (except explicit health endpoints) and emits sanitized `ApiResponse` JSON errors tagged with `X-Correlation-Id`.

## Low-End Hardware Engineering Choices

- Programmatic JavaFX controls + centralized `tokens.css` / `pos-theme.css` (no FXML reflection overhead).
- Bounded worker pools (`PosExecutors` & `BoundedExecutors`) with `ArrayBlockingQueue` limits.
- Desktop target JVM heap flags `-Xms128m -Xmx512m` in `javafx-maven-plugin`.
- SQLite `PRAGMA journal_mode=WAL` for resilient, fast local terminal caching.

## Core POS & Realtime Sync Architecture (Phase 4)

- **Flyway `V010__core_pos_enhancements.sql`** extends menu, table, and order tables with branch category visibility overrides, default modifier selection, `ON_HOLD` order status, `sent_to_kitchen` item locking, and tax snapshot metadata.
- **Outbox Dispatch**: Sending an order to kitchen writes an `ORDER_SENT_TO_KITCHEN` record to `outbox_events` inside the same database transaction as the order update.
- **Authoritative Branch State**: Multiple POS terminals stay consistent via optimistic locking (`version`), branch-scoped WebSocket change hints (`/ws/events`), and sequence-based REST catch-up (`/api/v1/events/recent`).

## Financial Ledger, Payments & Receipts (Phase 5)

- **Flyway `V011__payments_cash_receipts_and_attempts.sql`** adds `payment_attempts` (mutable state machine tracking external card terminal attempts & reconciliation), `drawer_events` (append-only hardware kick audit ledger), and `receipts`, while preserving `V005` append-only protections on `payments`, `payment_transactions`, `refunds`, and `cash_movements`.
- **Single Active Session Constraint**: Partial unique index `uq_cash_sessions_active_terminal` prevents concurrent active cash sessions on the same terminal.
- **Hardware Port Separation**: `PaymentTerminalPort`, `CashDrawerPort`, and `ReceiptPrinterPort` decouple financial use cases in `pos-application` from `pos-device-integration` (`VirtualCardTerminalGateway`, `VirtualCashDrawer`, `VirtualReceiptPrinter`, `EscPosReceiptFormatter`).

## Kitchen Display System, Routing & Printing (Phase 6)

- **Flyway `V012__kitchen_display_routing_and_printers.sql`** extends `V006` (`kitchen_stations`, `kitchen_tickets`, `kitchen_ticket_items`, `kitchen_ticket_events`) with `kitchen_station_assignments`, `kitchen_routing_rules`, `kitchen_display_configs`, `kitchen_processed_events`, `kitchen_unrouted_items`, and `kitchen_print_jobs`.
- **Idempotent Outbox Pipeline**: `OutboxKitchenDispatchAdapter` serializes immutable order/modifier snapshots via `KitchenPayloadCodecPort`, and `KitchenOutboxScheduler` / `KitchenOutboxConsumerService` consume `ORDER_SENT_TO_KITCHEN` & `ORDER_CANCELLED_IN_KITCHEN` events with PostgreSQL `SKIP LOCKED` claiming, retry backoff, and `DEAD_LETTER` handling.
- **Pure Domain Policies**: `KitchenRoutingEngine` (PRODUCT -> CATEGORY -> BRANCH_DEFAULT + multi-station fan-out) and `KitchenTicketStatePolicy` (state transitions, partial readiness, overdue detection, Expo gating) stay free of framework dependencies in `pos-domain`.


