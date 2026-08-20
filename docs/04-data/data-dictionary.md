---
title: Data Dictionary
status: draft
version: 0.2
last_updated: 2026-08-19
---

# users

- `id`: BIGINT identity primary key
- `email`: VARCHAR(320), trim 후 lowercase로 정규화한 Creator login identity, unique
- `password_hash`: VARCHAR(255), `{id}` prefix를 포함한 `DelegatingPasswordEncoder` hash only
- `display_name`: VARCHAR(100), Admin UI label
- `role`: V1 `ADMIN`
- `created_at`, `updated_at`: TIMESTAMPTZ, Java `Instant` lifecycle callback authority

# surveys

- `owner_id`: Creator
- `title`: VARCHAR(200), display title
- `description`: VARCHAR(5000), respondent intro
- `slug`: VARCHAR(64), lowercase public identity
- `privacy_notice`: VARCHAR(5000), optional notice
- `status`: DRAFT/OPEN/CLOSED
- `opened_at`, `closed_at`
- `deleted_at`
- timestamps

# questions

- `title`: VARCHAR(500)
- `description`: VARCHAR(2000), optional
- `scale_min_label`, `scale_max_label`: VARCHAR(100), SCALE only
- type-specific scale/number settings
- unique position per Survey

# question_options

- `label`: VARCHAR(500), Choice label
- position

# survey_responses

- `survey_id`
- `client_submission_id`
- `payload_hash`: CHAR(64), server canonical payload SHA-256 lowercase hex
- `submitted_at`

# answers

- `response_id`
- `question_id`
- `text_value`
- `numeric_value`

Question type에 따라 하나의 representation만 사용.

# answer_options

Choice answers selected options.

# Spring Session Tables

Spring Session JDBC table은 domain table은 아니지만 같은 PostgreSQL schema에서 Flyway로 version 관리한다. Framework schema auto-initialization은 사용하지 않는다.

- `SPRING_SESSION`: Spring Session 4.1.0 primary/session ID, creation/access/expiry, max inactive interval과 optional principal
- `SPRING_SESSION_ATTRIBUTES`: session primary ID + attribute name primary key, BYTEA value
- `SPRING_SESSION_IX1`: unique session ID
- `SPRING_SESSION_IX2`: expiry cleanup lookup
- `SPRING_SESSION_IX3`: principal lookup
- attributes foreign key: session delete 시 cascade

# Constraint Responsibility

- DB: identity/foreign key, unique position, slug/email/idempotency unique, simple nullability와 numeric CHECK
- Application/domain: Choice 최소 Option 수, Option ownership, type별 cross-row 조합, Response payload semantics

# Deferred Decisions

- live retention 기간: post-dogfooding data volume과 privacy review 뒤 `retention.md`에서 결정
- 향후 purge schema: V1 automatic purge가 없으므로 현재 schema scope 밖
