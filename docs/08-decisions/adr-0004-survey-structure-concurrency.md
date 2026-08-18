---
title: ADR-0004 Survey Structure Concurrency
status: accepted
version: 1.0
last_updated: 2026-08-18
---

# Status

`accepted`

# Context

첫 canonical Response가 저장되는 순간 Question semantics가 immutable이 된다. Structure mutation과 첫 Response submit이 application check만 통과해 동시에 commit되면 historical Answer의 의미가 바뀔 수 있다.

# Options Considered

1. Survey optimistic locking
2. Survey row pessimistic write lock
3. lock 없는 application check

# Decision

Structure mutation과 Response submit은 모두 transaction 시작부에서 동일 Survey row의 pessimistic write lock을 획득한다.

```text
Survey
→ Question / QuestionOption
→ SurveyResponse / Answer / AnswerOption
```

lock을 얻은 뒤 status, deletedAt, canonical Response 존재 여부와 Question structure를 다시 읽고 검증한다. lock 없는 application check는 허용하지 않는다.

# Rationale

- 두 transaction 중 하나가 먼저 commit되도록 직렬화해 invariant가 명확하다.
- Response insert가 Survey version을 자동 변경하지 않는 optimistic-only 설계의 누락 위험을 피한다.
- V1은 단일 API instance와 낮은 Survey별 traffic을 예상하므로 correctness를 throughput보다 우선한다.
- 별도 `structure_locked` state나 Survey version aggregate가 필요 없다.

# Consequences

- 동일 Survey의 submit과 구조 mutation은 lock 대기할 수 있다.
- 모든 관련 transaction이 같은 lock acquisition order를 지켜야 한다.
- lock wait는 bounded timeout을 사용한다. timeout/deadlock은 `503 Service Unavailable` / `TEMPORARILY_UNAVAILABLE`로 mapping하고 partial Response를 남기지 않는다. Respondent는 같은 `clientSubmissionId`로 안전하게 retry할 수 있다.
- concurrency integration test에서 mutation-first와 submit-first 순서를 모두 검증한다.

# Revisit When

Survey별 sustained concurrent submission이 실제 병목으로 확인되거나 다중 API instance에서 lock contention 목표를 다시 정의할 때 재검토한다.
