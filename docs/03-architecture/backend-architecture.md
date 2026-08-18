---
title: Backend Architecture
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Style

Modular monolith.

추천 package boundary:

```text
auth
survey
question
response
export
common
```

# 2. Layering

```text
Controller
→ Application/Service
→ Domain policy
→ Repository
```

JPA Entity를 API response로 직접 노출하지 않는다.

# 3. Transaction

- Survey structure mutation: transaction
- Response submit: single transaction
- OPEN/CLOSED transition: transaction
- CSV read: read-only transaction candidate

# 4. Concurrency

첫 Response와 Survey 구조 변경 race를 보호해야 한다.

구체 방식 후보:

- optimistic locking with version
- row lock
- policy check + locking

`TBD`이며 구현 전에 ADR/architecture 검토 필요.

# 5. Validation

DTO validation + domain validation + DB constraints의 다층 방어.

# 6. Error Mapping

공통 error code contract를 사용한다.

# 7. Observability

Actuator health, structured logs, request correlation ID 후보.
