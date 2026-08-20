---
title: CSV Export Requirements
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Goal

Creator가 외부 분석을 위해 Survey Response를 CSV로 내보낼 수 있다.

# 2. Encoding

UTF-8 with BOM.

Record와 field escaping은 RFC 4180 규칙을 따른다. 구현 뒤 Excel과 LibreOffice에서 한글, 줄바꿈, 쉼표, 큰따옴표 smoke test를 수행한다.

# 3. Columns

Metadata column 뒤에 Question position 순서로 answer column을 둔다.

```text
response_id
submitted_at
q_{questionId}: {questionTitle}
```

- `submitted_at`은 UTC ISO-8601로 출력한다.
- internal ID를 header에 포함해 같은 title과 title punctuation에도 column identity를 유지한다.
- optional unanswered value는 빈 field다.
- Question title 변경은 첫 Response 이후 lock되므로 export header 의미가 유지된다.
- SHORT_TEXT/LONG_TEXT는 원문 text, SINGLE_CHOICE는 `{optionId}: {optionLabel}`, SCALE/NUMBER는 canonical decimal text로 출력한다.

# 4. MULTIPLE_CHOICE

Option별 boolean column을 사용한다.

```text
q_{questionId}_option_{optionId}: {questionTitle} / {optionLabel}
```

각 cell은 `true` 또는 `false`다. 이 방식은 delimiter collision을 피하고 외부 분석에서 별도 parsing 없이 집계할 수 있다. 첫 canonical Response 이후 Option semantics가 lock되므로 header 의미도 유지된다.

# 5. Security

Creator owner authorization 필수.

CSV formula injection 위험을 고려한다.

Text와 label을 포함한 string cell에서 첫 non-whitespace 문자가 `=`, `+`, `-`, `@`이면 ASCII apostrophe(`'`)를 prefix한다. 이후 RFC 4180 escaping을 적용한다. 원본 Response는 변경하지 않고 export representation에만 적용한다.
