---
title: Data Dictionary
status: draft
version: 0.5
last_updated: 2026-08-21
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

`dev`에 통합된 Phase 3-B는 existing V5에 caller-owned Product insert/idempotency repository를 추가했다. 현재 Phase 3-C tree는 `(survey_id, client_submission_id)` canonical identity와 V6 Answer aggregate를 same-Survey lock 아래에서 HTTP replay/lifecycle validation과 원자적으로 결합한다.

# answers

- `id`: BIGINT identity primary key
- `response_id`: BIGINT, `survey_responses.id` FK, not null, aggregate-owned `ON DELETE CASCADE`
- `question_id`: BIGINT, `questions.id` FK, not null, historical identity를 보존하는 `NO ACTION`
- `text_value`: VARCHAR(5000), nullable, accepted decoded text를 trim/Unicode normalization 없이 저장
- `numeric_value`: NUMERIC(19,4), nullable, SCALE/NUMBER value
- `created_at`: TIMESTAMPTZ, not null
- `UNIQUE(response_id, question_id)`
- CHECK: `text_value`와 `numeric_value`는 동시에 non-null일 수 없음

Question type에 따라 text, numeric 또는 related `answer_options` 중 하나의 representation만 사용한다. DB는 text/numeric 동시 저장만 막고 exact type mapping, required semantics와 Option ownership은 locked application transaction이 검증한다. Optional unanswered Question은 Answer row를 만들지 않는다.

# answer_options

- `answer_id`: BIGINT, `answers.id` FK, not null, `ON DELETE CASCADE`
- `option_id`: BIGINT, `question_options.id` FK, not null, historical identity를 보존하는 `NO ACTION`
- `PRIMARY KEY(answer_id, option_id)`

SINGLE_CHOICE 하나/MULTIPLE_CHOICE 하나 이상의 distinct Option과 Question ownership은 application invariant다.

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

# Migration Ownership

Phase 2-A changeset은 V1/V2를 수정하지 않고 V3를 추가한다. Version ownership은 다음과 같다.

```text
V3  surveys
V4  questions + question_options
V5  survey_responses schema-only final V1 table
V6  answers + answer_options
```

V3 `surveys`는 Phase 2-A에서 구현됐다. Phase 2-B는 exact `V4__create_questions_and_options.sql`과 `V5__create_survey_responses.sql`을 추가했다. V5는 schema-only authority이며 Product SurveyResponse writer는 계속 존재하지 않는다.

Shared V1~V5 history는 immutable하다. Phase 3-B는 exact `V6__create_answers_and_answer_options.sql`로 `answers`와 `answer_options`만 추가했다. V6는 existing V5 `survey_responses`를 변경하지 않으며 persistent `structure_locked`, denormalized response count와 second Response authority를 만들지 않는다.

# Deferred Decisions

- live retention 기간: post-dogfooding data volume과 privacy review 뒤 `retention.md`에서 결정
- 향후 purge schema: V1 automatic purge가 없으므로 현재 schema scope 밖
