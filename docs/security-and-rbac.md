# Security, Authentication & RBAC Architecture

## 1. Authentication & Session Lifecycle

- **Password Storage:** `BCryptPasswordEncoder` (strength 12) via `PasswordHasherAdapter`. Plaintext passwords are never stored or returned in API payloads or audit logs.
- **Opaque Bearer Sessions:** On `POST /api/v1/auth/login`, `AuthenticationService` generates a 256-bit `SecureRandom` Base64URL token (`pos_sess_...`), returns the raw token once to the terminal, and persists only its **SHA-256 hex digest** in `user_sessions.token_hash`.
- **Session Validation:** `SessionAuthenticationFilter` validates every protected request against `user_sessions` (checking `revoked_at IS NULL` and `expires_at > now()`), verifies user `active` & not locked, verifies active branch access, and resolves live roles and permissions from the database so permission revocations take effect immediately.
- **Session Policies (configurable via `pos.security.*` / env vars):**
  - `POS_SESSION_TTL_MINUTES` (default `480` min = 8h shift)
  - `POS_MAX_ACTIVE_SESSIONS` (default `5` concurrent terminal sessions per user; oldest session is revoked automatically when exceeded)
  - `POS_MAX_FAILED_LOGINS` (default `5` consecutive failed attempts triggers temporary lockout)
  - `POS_LOCKOUT_MINUTES` (default `15` min)
  - `POS_THROTTLE_WINDOW_SECONDS` / `POS_THROTTLE_MAX_ATTEMPTS` (default `10` attempts per `60` s per `tenant:username`)
  - `POS_APPROVAL_TTL_MINUTES` (default `5` min)
- **Logout & Revocation:** `POST /api/v1/auth/logout`, `POST /api/v1/sessions/{sessionId}/revoke`, `POST /api/v1/auth/change-password`, and user deactivation (`POST /api/v1/users/{userId}/status`) immediately revoke affected sessions.

## 2. Tenant, Branch & Privilege Escalation Safeguards

- All user, role, branch-access, session, approval, and audit queries are scoped by `tenant_id` from the server-validated `AccessPrincipal`.
- Branch switching (`POST /api/v1/auth/switch-branch`) verifies `user_branch_access` and audits unauthorized attempts.
- Non-owner actors cannot create roles or assign roles containing permissions they do not personally hold.
- Protected admin templates (`SUPER_ADMIN`, `RESTAURANT_OWNER`) cannot be deactivated or stripped of core `roles.*` / `users.*` / `settings.manage` permissions.

## 3. Default Role Templates & Scope

| Role Code | Scope | Max Discount % | Summary |
|---|---|---|---|
| `SUPER_ADMIN` | `TENANT` | `100.00` | Full system administration across all modules |
| `RESTAURANT_OWNER` | `TENANT` | `100.00` | Full tenant ownership across branches, finance, and RBAC |
| `BRANCH_MANAGER` | `BRANCH` | `50.00` | Branch operations, refunds, overrides, staff, inventory, audit view |
| `ASSISTANT_MANAGER` | `BRANCH` | `25.00` | Shift supervision, refunds, discounts, basic inventory adjustments |
| `CASHIER` | `BRANCH` | `10.00` | Order entry, payments, cash session open/close |
| `WAITER` | `BRANCH` | `5.00` | Table management, order entry, kitchen ticket view |
| `KITCHEN_STAFF` | `BRANCH` | `0.00` | Kitchen display viewing and prep status updates |
| `CHEF` | `BRANCH` | `0.00` | Kitchen station management and inventory visibility |
| `INVENTORY_MANAGER` | `BRANCH` | `0.00` | Inventory balances, adjustments, transfers, purchasing |
| `ACCOUNTANT` | `TENANT` | `0.00` | Financial reports, exports, cash reconciliation, audit view |
| `AUDITOR` | `TENANT` | `0.00` | Read-only compliance inspection across modules and audit logs |

Seeding via `RbacSeedService.seedTenantDefaults` is strictly idempotent: existing customized roles are never overwritten.

## 4. API Endpoints (Phase 3)

- `POST /api/v1/auth/login` (public)
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/switch-branch`
- `POST /api/v1/auth/change-password`
- `GET /api/v1/users`, `POST /api/v1/users`, `PATCH /api/v1/users/{userId}`, `POST /api/v1/users/{userId}/status`
- `PUT /api/v1/users/{userId}/branches`, `PUT /api/v1/users/{userId}/roles`, `GET /api/v1/branches`
- `GET /api/v1/roles`, `POST /api/v1/roles`, `PUT /api/v1/roles/{roleId}`, `PUT /api/v1/roles/{roleId}/permissions`
- `GET /api/v1/permissions`
- `GET /api/v1/sessions`, `POST /api/v1/sessions/{sessionId}/revoke`
- `POST /api/v1/approvals/authorize`
- `GET /api/v1/audit-logs`
