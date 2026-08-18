---
title: FormDock Question Domain Model
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Types

```text
SHORT_TEXT
LONG_TEXT
SINGLE_CHOICE
MULTIPLE_CHOICE
SCALE
NUMBER
```

# 2. Question Fields

```text
id
surveyId
type
title
description
required
position
scaleMin
scaleMax
scaleMinLabel
scaleMaxLabel
numberMin
numberMax
createdAt
updatedAt
```

# 3. Type Rules

## SHORT_TEXT

- max 500 Unicode code points
- no options
- scale/number configuration은 모두 `null`

## LONG_TEXT

- max 5000 Unicode code points
- no options
- scale/number configuration은 모두 `null`

## SINGLE_CHOICE

- QuestionOption >= 2
- required: exactly one
- optional: zero or one
- scale/number configuration은 모두 `null`

## MULTIPLE_CHOICE

- QuestionOption >= 2
- required: >= 1
- V1 min/max 선택 수 설정 없음
- scale/number configuration은 모두 `null`

## SCALE

```text
1 <= scaleMin < scaleMax <= 10
```

- `scaleMin`, `scaleMax`는 필수 integer다.
- `scaleMinLabel`, `scaleMaxLabel`은 optional이다.
- response는 inclusive range의 integer만 허용한다.
- options와 number configuration은 허용하지 않는다.

## NUMBER

`NUMERIC(19,4)`.

- `numberMin`, `numberMax`는 optional `NUMERIC(19,4)`다.
- 둘 다 있으면 `numberMin <= numberMax`여야 한다.
- response는 설정된 inclusive range 안의 `NUMERIC(19,4)` 값만 허용한다.
- options와 scale configuration은 허용하지 않는다.

# 4. Answer Presence

- required Question은 type에 맞는 valid Answer가 반드시 존재해야 한다.
- optional unanswered Question은 Answer row와 public request item을 만들지 않는다.
- Text Answer는 blank-only 값을 허용하지 않으며, valid text의 leading/trailing whitespace는 보존한다.
- SINGLE_CHOICE Answer는 정확히 한 Option, MULTIPLE_CHOICE Answer는 하나 이상의 distinct Option을 선택한다.
- SCALE/NUMBER Answer는 하나의 numeric value를 가진다.

# 5. Position

```text
UNIQUE(survey_id, position)
```

최종 저장 시 gap 없이 정규화한다.

# 6. Option

```text
id
questionId
label
position
```

다른 Question의 Option을 선택할 수 없다.

# 7. Validation Authority

Frontend는 UX validation.

최종 authority는 Backend.

현재 type에서 사용하지 않는 type-specific field는 무시하지 않고 `null`이어야 한다. 잘못된 field 조합이나 non-Choice Option은 request를 거절한다.

- 단일 row의 nullability, numeric bounds, position uniqueness처럼 단순한 invariant는 가능한 범위에서 DB CHECK/UNIQUE로도 보호한다.
- Choice Option 최소 개수, Option ownership, type별 cross-row 조합과 Response 의미 검증은 application/domain validation이 소유한다.

# 8. Core Invariants

- Question은 하나의 Survey에 속함
- position unique per Survey
- Choice type만 Option 보유
- Choice type은 최소 2 Option
- SCALE/NUMBER 범위 검증
- 미사용 type-specific field는 null
- first Response 이후 semantics immutable
