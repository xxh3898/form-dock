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

- max 500 chars
- no options

## LONG_TEXT

- max 5000 chars
- no options

## SINGLE_CHOICE

- QuestionOption >= 2
- required: exactly one
- optional: zero or one

## MULTIPLE_CHOICE

- QuestionOption >= 2
- required: >= 1
- V1 min/max 선택 수 설정 없음

## SCALE

```text
1 <= scaleMin < scaleMax <= 10
```

integer response only.

## NUMBER

`NUMERIC(19,4)`.

Optional `numberMin`, `numberMax`.

# 4. Position

```text
UNIQUE(survey_id, position)
```

최종 저장 시 gap 없이 정규화한다.

# 5. Option

```text
id
questionId
label
position
```

다른 Question의 Option을 선택할 수 없다.

# 6. Validation Authority

Frontend는 UX validation.

최종 authority는 Backend.

# 7. Core Invariants

- Question은 하나의 Survey에 속함
- position unique per Survey
- Choice type만 Option 보유
- Choice type은 최소 2 Option
- SCALE/NUMBER 범위 검증
- first Response 이후 semantics immutable
