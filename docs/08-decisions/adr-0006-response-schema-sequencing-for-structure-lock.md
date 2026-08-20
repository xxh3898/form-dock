---
title: ADR-0006 Response Schema Sequencing for Structure Lock
status: accepted
version: 1.0
last_updated: 2026-08-20
---

# Status

`accepted`

# Context

[ADR-0003](adr-0003-open-survey-mutation-policy.md)는 첫 canonical Response 이후 Question semantics를 immutable로 유지한다. [ADR-0004](adr-0004-survey-structure-concurrency.md)는 모든 structure mutation이 Survey row의 pessimistic write lock을 얻은 뒤 canonical Response 존재 여부를 다시 확인하도록 요구한다.

Phase 2는 Question mutation과 structure lock을 Phase 3 Public Response runtime보다 먼저 구현한다. 이 순서에서 Response persistence authority가 없으면 Phase 2는 temporary stub, 후속 semantic rewrite 또는 premature Response runtime 중 하나에 의존하게 된다.

# Options Considered

1. Phase 2에서 final `survey_responses` table만 schema-only lock authority로 생성한다.
2. Phase 2에서 `survey_responses`, `answers`, `answer_options` 전체 schema를 미리 생성한다.
3. Response schema와 hard structure lock을 모두 Phase 3까지 미룬다.
4. Survey에 mutable `response_count` 또는 `structure_locked` authority를 저장한다.

# Decision

Option 1을 선택한다.

Phase 2는 Question structure mutation이 real canonical authority를 조회할 수 있도록 final `survey_responses` table을 schema-only로 생성할 수 있다.

```text
Phase 2
→ final survey_responses schema 생성
→ Survey write lock 안에서 canonical row existence 조회
→ SurveyResponse row insert 금지
→ Public Response Product runtime 금지

Phase 3
→ Public Survey submission API
→ canonicalization / payloadHash / idempotency runtime
→ 최초 canonical SurveyResponse row insert
→ Answer / AnswerOption schema와 persistence
→ respondent validation과 atomic submission
```

Phase 2 structure mutation transaction은 다음 authority를 사용한다.

```text
BEGIN TX
→ Survey row PESSIMISTIC_WRITE
→ owner, status, deletedAt 재검증
→ SELECT EXISTS canonical survey_responses row
→ 존재하면 STRUCTURE_LOCKED
→ Question / QuestionOption mutation
→ COMMIT
```

Phase 2에는 canonical row를 생성하는 authorized runtime이 없으므로 existence query는 정상적으로 false를 반환한다. 하지만 constant false, mock adapter 또는 mutable lock flag로 대체하지 않고 final table을 실제로 조회한다. Phase 3가 row creation을 활성화하면 같은 query가 semantic rewrite 없이 canonical rows를 관찰한다.

# Schema Boundary

Phase 2 `survey_responses` schema는 [Response Domain Model](../01-domain/response-model.md)의 final V1 identity와 호환돼야 한다.

```text
id
survey_id
client_submission_id
payload_hash
submitted_at

UNIQUE(survey_id, client_submission_id)
```

실제 migration version과 file split은 implementation 시점의 immutable Flyway history에서 결정한다. Shared `V1__create_users.sql`과 `V2__create_spring_session.sql`을 수정하거나 이름을 바꾸지 않는다.

# Rationale

- ADR-0004를 첫 Question mutation부터 실제 canonical authority로 만족한다.
- Survey를 structure mutation과 submission의 공통 serialization point로 유지한다.
- 별도 `structure_locked` state나 denormalized response count authority를 만들지 않는다.
- Phase 3 시작 때 Question mutation transaction을 다시 설계하지 않는다.
- Response Product runtime은 계속 닫은 채 필요한 최소 schema만 선행한다.
- `answers`와 `answer_options`를 사용 전에 미리 도입하지 않는다.

# Consequences

- 하나의 Response-domain table이 Response Product capability보다 먼저 존재한다.
- Phase 2 migration review는 schema creation과 Product runtime authorization을 명확히 구분해야 한다.
- Phase 3 migration numbering은 `survey_responses`가 이미 존재한다는 사실에서 이어져야 한다.
- Phase 2 integration test는 real `survey_responses` existence query가 structure mutation transaction 안에서 실행됨을 검증해야 한다.
- Phase 3 concurrency test는 mutation-first와 submit-first ordering에서 같은 Survey lock과 existence authority를 검증해야 한다.

# Rejected Alternatives

- Full Response schema 선행은 Phase 2에 필요 없는 Answer persistence와 review surface를 확대하므로 거절한다.
- Phase 3 retrofit은 Phase 2 structure lock을 불완전하게 만들고 후속 semantic rewrite를 요구하므로 거절한다.
- Mutable flag/count authority는 canonical Response rows와 diverge할 수 있는 두 번째 source of truth이므로 거절한다.

# Explicit Non-Authorization

이 결정은 Phase 2 implementation authorization이 아니다. 다음은 계속 승인되지 않는다.

- Survey/Question application 또는 Flyway implementation
- Public Response API와 SurveyResponse insert runtime
- Answer/AnswerOption schema와 runtime
- idempotency, Result, CSV와 respondent UX
- Production migration, deployment, Secret 또는 live-data operation

Survey Domain / Phase 2와 Production은 별도 entry gate가 승인할 때까지 `NOT AUTHORIZED`다.

# Relationship to Existing Decisions

이 ADR은 ADR-0002, ADR-0003, ADR-0004를 supersede하지 않는다. Cross-phase persistence sequencing만 소유하며 relational Response model, first-Response immutability와 Survey-row locking contract를 그대로 따른다.
