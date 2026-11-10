# Payment Processing, Cash Management & Receipts (Phase 5)

## 1. Payment Lifecycle & Idempotency
- **Authoritative Local Server Processing**: Clients never submit calculated totals or settle orders locally. `PaymentService` computes `remainingDue = max(0, orderTotal - netCompletedPaid)` using exact `BigDecimal` + `Money` arithmetic (`HALF_UP`, scale 2).
- **Idempotency**: Both `payment_attempts` and `payments` enforce unique `(tenant_id, branch_id, idempotency_key)` constraints. Re-submitting an existing key returns the authoritative order payment summary without double-charging.
- **Explicit Attempt Lifecycle (`PaymentAttemptStatus` / `PaymentStatePolicy`)**:
  - `PENDING` -> `PROCESSING` -> `COMPLETED` / `FAILED` / `CANCELLED` / `UNCERTAIN`
  - `COMPLETED` -> `PARTIALLY_REFUNDED` -> `REFUNDED`
- **Append-Only Financial Ledger**: Settled rows in `payments`, ledger events in `payment_transactions`, refund entries in `refunds`, cash entries in `cash_movements`, and drawer kicks in `drawer_events` are protected against mutation by PostgreSQL `prevent_append_only_mutation()` triggers (`trg_payments_immutable`, `trg_payment_transactions_immutable`, `trg_refunds_immutable`, `trg_cash_movements_immutable`, `trg_drawer_events_immutable`).
- **PCI DSS Scope Minimization**: Raw card PAN, CVV, PIN, and magnetic track data are never accepted, stored, or logged. Only masked PAN (e.g., `**** **** **** 4242`), card brand, auth code, provider transaction ID, and terminal reference are retained.

## 2. Partial Tenders, Split Payments & Change
- Cash tenders support excess `tenderedAmount` with automatic `changeAmount = tenderedAmount - appliedAmount`, posting only the net applied sale amount to `CASH_SALE`.
- Non-cash tenders reject overpayment (`OVERPAYMENT_NOT_ALLOWED`).
- Partial payments keep the order open with updated `paidAmount` and `remainingDue`; once `remainingDue == 0`, the order transitions to `PAID` and releases any occupied dine-in table to `CLEANING`.
- Already-`PAID`, `CANCELLED`, or `REFUNDED` orders reject further payment attempts (`ORDER_NOT_PAYABLE`).

## 3. External Card Terminal Abstraction & Reconciliation
- Provider-independent ports in `pos-domain`: `PaymentTerminalPort`, `PaymentAuthorizationPort`, `PaymentStatusQueryPort`, and `PaymentReversalPort`.
- Default virtual adapter: `VirtualCardTerminalGateway` in `pos-device-integration` supports deterministic simulation hints (`APPROVED`, `DECLINED`, `TIMEOUT_THEN_APPROVED`, `TIMEOUT_THEN_DECLINED`, `CANCELLED`, `REVERSE_FAIL`) for automated tests without vendor hardware.
- **Uncertain Outcomes**: If a card terminal times out, the attempt transitions to `UNCERTAIN`, leaves the order unpaid, appears in `GET /api/v1/payments/reconciliation`, and requires querying `PaymentStatusQueryPort` (`POST /api/v1/payments/attempts/{attemptId}/reconcile`) before retrying.

## 4. Refunds & Reversals
- Every refund is a separate append-only `refunds` row referencing `order_id` and `original_payment_id` (original `payments` rows are never edited).
- Cumulative completed refunds cannot exceed the original completed payment amount (`REFUND_LIMIT_EXCEEDED`).
- Refunds exceeding `pos.payment.refund-approval-threshold` (default `25.00`) require `supervisorApprovalId` for users without `payments.reverse` override authority; `SupervisorApprovalService` and `RefundService` enforce non-self-approval.
- Failed external reversals remain persisted in `refunds` (`status = FAILED`) without reducing `order.paidAmount`.
- Refunds never automatically adjust inventory stock balances.

## 5. Cash Sessions, Derived Balance & Drawer Integration
- Partial unique index `uq_cash_sessions_active_terminal` enforces at most one active (`OPEN` / `REOPENED`) cash session per terminal per branch.
- Expected cash balance is always derived dynamically from append-only `cash_movements`: `OPENING_FLOAT + CASH_SALE + PAY_IN + DEPOSIT - CASH_REFUND - PAY_OUT - WITHDRAWAL - SAFE_DROP`.
- Cash drawer kicks are authorized by Local Server (`cash.drawer.open` for cash sales; `cash.drawer.open_without_sale` + mandatory reason for manual opens) and audited in `drawer_events` and `audit_logs`.

## 6. ESC/POS Receipt Generation & Printing
- `EscPosReceiptFormatter` supports configurable `paperWidth` (`32..64`, default `42`), encodings (`UTF-8`, `CP864`, `WPC1256`), languages (`EN`, `AR`, `BILINGUAL`), customer/payment confirmation/refund/duplicate/cash-session closing reports, and ESC/POS init + cut commands.
- Receipts are generated and persisted independently in `receipts`; `PRINTER_UNAVAILABLE` or `FAILED` hardware statuses are recorded cleanly on the receipt and returned as warnings without rolling back completed payments. Duplicate reprints mark the copy as duplicate and increment `print_count`.

## 7. Hardware Simulation Notice & Verification
- Physical serial/USB/TCP thermal printers, physical DK-kick cash drawers, and certified bank EMV terminals are **not** connected in this workspace; tests and default configurations run against `VirtualReceiptPrinter`, `VirtualCashDrawer`, and `VirtualCardTerminalGateway`.
- Run full Phase 5 verification (including PostgreSQL triggers, constraints, idempotency, RBAC, and virtual hardware failure/recovery scenarios) via:
```powershell
.\mvnw.cmd clean verify
```
