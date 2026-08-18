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
- idempotency: 최초 201, 동일 replay 200, conflict 409, concurrent duplicate
- concurrency: first Response와 structure mutation의 두 lock 순서 모두
- public status: unavailable GET 404, CLOSED 신규 submit 409, CLOSED 기존 replay 200/409
- JDBC session restart/expiry/invalidation
- CSRF: login/logout/Admin mutation 보호와 exact Public submit 제외
- Creator bootstrap zero/existing/partial/conflicting input

## REST Docs

API contract와 controller behavior 동기화.

# 2. Frontend

- component/unit
- form validation
- Builder behavior
- respondent flow
- CSRF token refresh와 error-code mapping

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
- UTF-8 BOM, RFC 4180, formula-like text, MULTIPLE_CHOICE boolean columns
- close

# 5. Production

public read/smoke는 mutation 최소화.

실제 dogfooding survey로 최종 end-to-end 확인.
