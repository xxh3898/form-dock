---
title: Database Migration Policy
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Tool

Flyway.

Production schema를 수동 변경하지 않는다.

# 2. Rules

- migration immutable after shared environment 적용
- 수정 대신 새 migration 추가
- backward-compatible additive migration 우선
- destructive migration은 별도 approval 필요
- startup Flyway failure 시 application readiness 실패

# 3. Naming

예:

```text
V1__create_users.sql
V2__create_surveys.sql
V3__create_questions.sql
V4__create_responses.sql
```

실제 slicing은 구현 시 조정 가능.

# 4. Rollback

Flyway migration 자체의 자동 down migration은 기본 제공하지 않는다.

Release rollback 시 DB가 이전 app과 호환되는지 사전 검증한다.

# 5. Seed

Initial Creator credential secret을 migration source에 직접 저장하지 않는다.

Spring Session JDBC table을 포함한 production schema는 Flyway가 소유한다. Framework의 production schema auto-initialization은 사용하지 않는다.
