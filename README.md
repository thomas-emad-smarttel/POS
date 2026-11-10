# Restaurant POS Platform

Commercial-grade, offline-first, multi-branch Restaurant POS system built with **Java 21 LTS target**, **Spring Boot**, **JavaFX (programmatic UI + CSS Design Tokens)**, **PostgreSQL + Flyway**, and **SQLite** terminal caching.

## Prerequisites

- **Java:** JDK 21 LTS or compatible newer JDK (builds use `--release 21`; tested on Eclipse Adoptium JDK 25).
- **Maven:** Not required globally — use the included Maven Wrapper (`mvnw` / `mvnw.cmd`).
- **PostgreSQL:** Required starting Phase 2 (`dev` profile allows Phase 1 startup without a live database).

## Build & Verify

### Windows (PowerShell)
```powershell
.\mvnw.cmd clean verify
```

### Linux / macOS
```bash
./mvnw clean verify
```

## Running Applications (Phase 1 Foundation)

### 1. Local Server (Branch Server — default port `8080`)
```powershell
.\mvnw.cmd -pl pos-local-server spring-boot:run
```
Health check: `GET http://localhost:8080/api/v1/system/health`

### 2. Cloud Backend (Optional Central Server — default port `8090`)
```powershell
.\mvnw.cmd -pl pos-cloud spring-boot:run
```
Health check: `GET http://localhost:8090/api/v1/cloud/health`

### 3. Desktop POS Terminal (JavaFX)
```powershell
.\mvnw.cmd -pl pos-desktop javafx:run
```
Custom Local Server URL:
```powershell
.\mvnw.cmd -pl pos-desktop javafx:run "-Djavafx.args=" "-Dpos.server.url=http://192.168.1.10:8080"
```

## Documentation

- [Architecture & Module Boundaries](docs/architecture.md)
- [Security, Authentication & RBAC Guide](docs/security-and-rbac.md)
- [Core POS Workflow & Realtime Sync Guide](docs/core-pos.md)
- [Payments, Cash Management & Receipts Guide](docs/payments-cash-and-receipts.md)
- [Kitchen Display System & Routing Guide](docs/kitchen-display-system.md)
