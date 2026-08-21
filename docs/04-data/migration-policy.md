---
title: Database Migration Policy
status: draft
version: 0.7
last_updated: 2026-08-21
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

Current versioned history:

```text
V1__create_users.sql
V2__create_spring_session.sql
V3__create_surveys.sql
V4__create_questions_and_options.sql
V5__create_survey_responses.sql
V6__create_answers_and_answer_options.sql
```

V1과 V2는 Phase 1 Creator Foundation이 소유하며 shared history에서 immutable하다. `users` business schema와 Spring Session infrastructure schema를 별도 migration으로 유지한다.

Issue #22 entry에서 versioned file이 V1/V2뿐임을 재검증했고 Phase 2-A가 V1/V2를 수정하지 않은 채 V3 surveys를 추가했다. Phase 2-B는 V4 questions/question_options, V5 final `survey_responses` 순서를 유지해 추가했다. Phase 3-B current tree는 V1..V5를 수정하지 않고 V6 answers/answer_options만 추가한다. Shared environment 적용 뒤 versioned migration을 수정하거나 이름을 바꾸지 않는다.

[ADR-0006](../08-decisions/adr-0006-response-schema-sequencing-for-structure-lock.md)에 따라 Phase 2는 Question structure mutation의 canonical existence authority로 final `survey_responses` table만 schema-only로 생성한다. Phase 2 Product application은 이 table을 structure-lock `EXISTS`와 Admin `COUNT`로 읽을 수 있지만 SurveyResponse row를 생성하지 않는다.

Disposable PostgreSQL integration test는 lock/count behavior 검증을 위해 `survey_responses` fixture row를 SQL/test support로 직접 insert할 수 있다. Test fixture는 Product repository/service/API writer로 노출하지 않는다.

Phase 3-B의 V6는 `answers`와 `answer_options`만 생성한다. `answers.response_id`와 `answer_options.answer_id`는 aggregate ownership에 따라 cascade하며 Question/Option reference는 historical semantics 보존을 위해 NO ACTION이다. Existing V5 `survey_responses`를 수정하거나 재생성하지 않는다. Phase 3-B는 caller-owned canonical SurveyResponse/Answer persistence와 payload hash/idempotency primitive를 추가하고, Public submission transaction과 HTTP behavior는 Phase 3-C가 소유한다. Full Response를 대신하는 임시 lock flag/count authority를 만들지 않는다.

`V2__create_spring_session.sql`은 Spring Session JDBC 4.1.0 JAR의 PostgreSQL vendor schema를 source로 사용한다. Flyway history에 적용된 뒤 V1/V2를 수정하지 않고 필요한 변경은 다음 version migration으로 추가한다.

# 4. Rollback

Flyway migration 자체의 자동 down migration은 기본 제공하지 않는다.

Release rollback 시 DB가 이전 app과 호환되는지 사전 검증한다.

# 5. Seed

Initial Creator credential secret을 migration source에 직접 저장하지 않는다.

Spring Session JDBC table을 포함한 production schema는 Flyway가 소유한다. Framework의 production schema auto-initialization은 사용하지 않는다.
