---
title: ADR-0002 Survey Response Model
status: accepted
version: 1.0
last_updated: 2026-08-18
---

# Status

`accepted`

# Context

설문 플랫폼은 다양한 Question type의 Answer를 안정적으로 저장해야 한다.

# Options

1. 전체 Response JSONB
2. Question별 전용 column
3. Relational SurveyResponse / Answer / AnswerOption

# Decision

```text
Relational model
SurveyResponse
→ Answer
→ AnswerOption
```

# Rationale

- FK/constraint 활용
- question-level aggregation 쉬움
- CSV/개별 조회 명확
- 범용 Survey에도 적용 가능
- 특정 프로젝트 컬럼 불필요

# Consequences

- query join 증가
- type-specific validation 필요
- polymorphic Answer 모델 관리 필요

# Explicit Rejection

프로젝트별 `daily_challenge_interest` 같은 column은 만들지 않는다.
