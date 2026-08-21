---
title: Backend Architecture
status: draft
version: 0.4
last_updated: 2026-08-21
---

# 1. Style

Modular monolith.

초기 package boundary:

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
- CSV read: read-only transaction

# 4. Concurrency

첫 Response와 Survey 구조 변경 race를 보호해야 한다.

V1은 동일 Survey row의 pessimistic write lock을 사용한다.

- Question/Option 구조 mutation transaction은 Survey row를 먼저 write lock한다.
- Response submit transaction도 Survey row를 먼저 write lock한 뒤 status, deletedAt, canonical Response 존재와 Question structure를 검증한다.
- lock 획득 순서는 Survey → Question/Option → SurveyResponse/Answer로 고정한다.
- transaction이 lock을 얻은 뒤 canonical state를 다시 읽으므로 application check만으로 race를 처리하지 않는다.

낮은 V1 traffic에서 throughput보다 historical Response 의미의 correctness를 우선한다. 자세한 결정은 [ADR-0004](../08-decisions/adr-0004-survey-structure-concurrency.md)를 따른다.

# 5. Validation

DTO validation + domain validation + DB constraints의 다층 방어.

# 6. Error Mapping

공통 error code contract를 사용한다.

# 7. Observability

Actuator health는 scaffold dependency에 포함한다. Structured logs와 request correlation ID의 exact format은 Production Readiness Phase로 deferred하며 application scaffold blocker가 아니다.

# 8. Creator Authentication Boundary

```text
Auth Controller
→ Creator Session Service
→ AuthenticationManager / Creator AuthenticationProvider
→ UserRepository
→ PostgreSQL
```

API DTO는 JPA `User`를 노출하지 않는다. Authenticated session에는 id/email/displayName/role만 가진 serializable `CreatorPrincipal`을 저장하고 password hash는 포함하지 않는다. REST login은 session fixation strategy 적용과 `SecurityContextRepository` explicit save를 service boundary 한 곳에서 수행한다.

# 9. Phase 2-A Survey Boundary

```text
SurveyController
→ SurveyService / Survey domain invariant
→ owner-scoped SurveyRepository
→ PostgreSQL V3 surveys
```

Phase 2-A는 `CreatorPrincipal.id()`를 owner authority로 사용하고 non-deleted owner scope 안에서만 list/detail/mutation을 수행한다. Generated slug collision retry는 각 insert attempt를 독립 transaction으로 실행하며 database unique constraint를 최종 authority로 사용한다. Question/Response persistence가 아직 없으므로 detail/list DTO의 `questions=[]`, `responseCount=0`, `structureLocked=false`는 별도 adapter/query 없이 capability boundary에서 직접 보장한다.

# 10. Phase 2-B Question and Structure-Lock Boundary

```text
SurveyService canonical read
→ ordered QuestionRepository read
→ read-only SurveyResponse COUNT / EXISTS
→ unchanged Survey DTO wire shape
```

Phase 2-B는 V4 Question/Option persistence와 V5 schema-only `survey_responses` authority를 추가한다. Survey list는 visible Survey ID 전체를 grouped COUNT 한 번으로 읽고, detail/create/PATCH는 ordered Questions/Options와 real COUNT/EXISTS를 사용한다. Product runtime에는 SurveyResponse save/delete repository, service 또는 API가 없다.

Phase 2-C structure mutation은 caller-owned transaction에서 `SurveyStructureGuard`를 사용한다. Guard는 transaction-local PostgreSQL `lock_timeout`을 설정하고 active owner Survey row를 `PESSIMISTIC_WRITE`로 잠근 뒤 current status/deleted state와 real V5 EXISTS를 읽는다. Canonical Response가 있으면 `SURVEY_STRUCTURE_LOCKED`, bounded timeout/deadlock이면 safe `TEMPORARILY_UNAVAILABLE`이다.

# 11. Phase 2-C Builder Mutation Boundary

Question create/update/delete/reorder는 Survey guard를 첫 authority로 사용한 같은 transaction에서 aggregate를 변경한다. Immediate Question/Option position UNIQUE constraint를 유지하기 위해 reorder와 renormalization은 current rows를 unused high position range로 옮겨 flush한 뒤 final zero-based gapless position을 적용한다.

OPEN/CLOSE와 duplicate source snapshot은 canonical Response 존재를 거절하지 않는 lower-level active-owner Survey write lock을 사용한다. OPEN은 lock 이후 persisted Question/Option structure를 검증하고, duplicate는 source lock부터 fresh DRAFT Survey/Question/Option insert까지 candidate별 independent transaction으로 묶어 slug collision이나 copy failure 시 partial aggregate를 남기지 않는다. Phase 2 Product code의 `survey_responses` 접근은 계속 COUNT/EXISTS read-only다.

# 12. Phase 3 Public Survey and Response Boundary

Phase 3 Entry는 다음 architecture contract만 승인하며 현재 runtime을 추가하지 않는다.

```text
3-A Public Survey query
→ 3-B Response data/canonicalization
→ 3-C atomic Public submit
→ 3-D Respondent frontend
```

Public Survey query는 OPEN + not-deleted slug만 respondent-safe DTO로 projection하고 DRAFT/CLOSED/deleted/unknown을 같은 404 shape로 은닉한다. 내부 Survey ID, owner, Admin timestamp, responseCount와 structureLocked는 public DTO에 포함하지 않는다.

Public submit transport guard는 exact JSON/content-size/rate/CSRF boundary만 담당하고 Product transaction 밖에서 Response write를 하지 않는다. Admitted request의 transaction authority는 다음과 같다.

```text
resolve public Survey identity
→ BEGIN TX
→ Survey PESSIMISTIC_WRITE
→ deleted/lifecycle/ordered Question+Option 재조회
→ existing clientSubmissionId canonical replay 판정
→ 신규인 경우 OPEN + full Answer validation
→ SurveyResponse → Answer → AnswerOption atomic insert
→ COMMIT
```

Deleted/unknown/DRAFT는 404다. CLOSED는 existing same replay 200, existing conflicting replay 409, 신규 identity 409다. Unique race는 existing canonical row를 재조회해 200/409로 수렴한다. Lock order는 ADR-0004의 `Survey → Question/Option → SurveyResponse/Answer/AnswerOption`을 그대로 사용하며 second lock/version/count authority를 만들지 않는다.

Phase 3-B의 future V6는 `answers`/`answer_options`만 추가하고 existing V5 `survey_responses`를 재작성하지 않는다. Phase 3-C는 mutation-first와 submit-first PostgreSQL ordering, bounded 503와 partial aggregate 0을 deterministic integration test로 증명한다. Result/Response read, aggregation과 CSV는 Phase 4까지 backend에 추가하지 않는다.
