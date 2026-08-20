---
title: Backend Architecture
status: draft
version: 0.3
last_updated: 2026-08-20
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

후속 Phase 2-C structure mutation은 caller-owned transaction에서 `SurveyStructureGuard`를 사용한다. Guard는 transaction-local PostgreSQL `lock_timeout`을 설정하고 active owner Survey row를 `PESSIMISTIC_WRITE`로 잠근 뒤 current status/deleted state와 real V5 EXISTS를 읽는다. Canonical Response가 있으면 `SURVEY_STRUCTURE_LOCKED`, bounded timeout/deadlock이면 safe `TEMPORARILY_UNAVAILABLE`이다. 이 slice에는 Question mutation endpoint가 없다.
