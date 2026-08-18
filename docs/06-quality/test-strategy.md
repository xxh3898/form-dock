---
title: Test Strategy
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Backend

## Unit

- domain policy
- validation
- lifecycle
- payload canonicalization

## Integration

Testcontainers PostgreSQL 사용.

- JPA constraints
- Flyway
- ownership
- Response transaction
- idempotency
- concurrency critical path

## REST Docs

API contract와 controller behavior 동기화.

# 2. Frontend

- component/unit
- form validation
- Builder behavior
- respondent flow

E2E 범위는 V1 핵심 flow 중심.

# 3. Infrastructure

- Docker build
- Compose config
- health checks
- ARM64

# 4. Manual Smoke

- Creator login
- Survey create
- OPEN
- mobile response
- result view
- CSV
- close

# 5. Production

public read/smoke는 mutation 최소화.

실제 dogfooding survey로 최종 end-to-end 확인.
