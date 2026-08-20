---
title: Data Dictionary
status: draft
version: 0.3
last_updated: 2026-08-20
---

# users

- `id`: BIGINT identity primary key
- `email`: VARCHAR(320), trim 후 lowercase로 정규화한 Creator login identity, unique
- `password_hash`: VARCHAR(255), `{id}` prefix를 포함한 `DelegatingPasswordEncoder` hash only
- `display_name`: VARCHAR(100), Admin UI label
- `role`: V1 `ADMIN`
- `created_at`, `updated_at`: TIMESTAMPTZ, Java `Instant` lifecycle callback authority

# surveys

- `id`: BIGINT identity primary key
- `owner_id`: BIGINT, `users.id` FK, not null
- `title`: VARCHAR(200), trimmed non-blank display title, not null
- `description`: VARCHAR(5000), nullable respondent intro
- `slug`: VARCHAR(64), lowercase globally unique public identity, not null and soft delete 후에도 reserved
- `privacy_notice`: VARCHAR(5000), nullable notice
- `status`: VARCHAR, `DRAFT`/`OPEN`/`CLOSED` CHECK, not null
- `opened_at`: TIMESTAMPTZ, nullable, first OPEN timestamp and 이후 immutable
- `closed_at`: TIMESTAMPTZ, nullable, current CLOSED period timestamp; reopen 시 null
- `deleted_at`: TIMESTAMPTZ, nullable soft-delete authority
- `created_at`, `updated_at`: TIMESTAMPTZ, not null

# questions

- `id`: BIGINT identity primary key
- `survey_id`: BIGINT, `surveys.id` FK, not null
- `type`: VARCHAR, six approved Question type CHECK, not null
- `title`: VARCHAR(500), trimmed non-blank title, not null
- `description`: VARCHAR(2000), nullable
- `required`: BOOLEAN, not null
- `position`: INTEGER, not null, `UNIQUE(survey_id, position)` and application-normalized `0..n-1`
- `scale_min`, `scale_max`: INTEGER, nullable, SCALE only with `1 <= min < max <= 10`
- `scale_min_label`, `scale_max_label`: VARCHAR(100), nullable, SCALE only
- `number_min`, `number_max`: NUMERIC(19,4), nullable, NUMBER only and both present이면 `min <= max`
- `created_at`, `updated_at`: TIMESTAMPTZ, not null

# question_options

- `id`: BIGINT identity primary key
- `question_id`: BIGINT, `questions.id` FK, not null
- `label`: VARCHAR(500), trimmed non-blank Choice label, not null
- `position`: INTEGER, not null, `UNIQUE(question_id, position)` and application-normalized `0..n-1`

# survey_responses

- `id`: BIGINT identity primary key
- `survey_id`: BIGINT, `surveys.id` FK, not null
- `client_submission_id`: UUID, not null
- `payload_hash`: CHAR(64), server canonical payload SHA-256 lowercase hex, not null
- `submitted_at`: TIMESTAMPTZ, not null
- `UNIQUE(survey_id, client_submission_id)`

Phase 2-A는 V3 `surveys`만 소유하므로 Question과 canonical Response가 존재할 capability 자체가 없다. 이 경계에서 final Survey DTO는 `questions=[]`, `responseCount=0`, `structureLocked=false`를 반환한다. 이 값은 database column이나 임시 persistence authority가 아니라 missing capability에서 논리적으로 보장되는 API 값이며, Phase 2-A는 Question/Response repository, query, constant-false adapter 또는 stub을 만들지 않는다.

Phase 2-B가 V4/V5를 추가하면 DTO wire shape를 바꾸지 않고 `questions`는 real `questions`/`question_options` persistence, `responseCount`는 `survey_responses` `COUNT`, `structureLocked`는 `survey_responses` `EXISTS`를 authority로 사용한다. Phase 2 Product application에는 SurveyResponse insert repository/service/API가 없으며, 모든 Question structure mutation은 처음부터 real V5 `EXISTS`를 사용한다.

V5 이후 disposable PostgreSQL integration test는 structure-lock behavior를 증명하기 위해 canonical fixture row를 직접 insert할 수 있다. 이는 Product runtime writer authorization이 아니다.

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

# Phase 2 Migration Ownership

Current immutable history가 V1/V2뿐인 entry 기준으로:

```text
V3  surveys
V4  questions + question_options
V5  survey_responses schema-only final V1 table
```

Phase 3가 후속 migration에서 `answers`와 `answer_options` schema를 소유한다. Phase 2에는 persistent `structure_locked`, denormalized response count와 Answer schema가 없다.

# Deferred Decisions

- live retention 기간: post-dogfooding data volume과 privacy review 뒤 `retention.md`에서 결정
- 향후 purge schema: V1 automatic purge가 없으므로 현재 schema scope 밖
