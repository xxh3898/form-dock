---
title: CSV Export Requirements
status: draft
version: 0.3
last_updated: 2026-08-23
---

# 1. Goal

Creator가 외부 분석을 위해 Survey Response를 CSV로 내보낼 수 있다.

# 2. Encoding

UTF-8 BOM bytes `EF BB BF`를 정확히 한 번 사용한다.

Record와 field escaping은 RFC 4180 규칙을 따르고 record separator는 CRLF(`\r\n`)다. 구현 뒤 Excel과 LibreOffice에서 한글, 줄바꿈, 쉼표, 큰따옴표 smoke test를 수행한다.

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
- Response row는 `submitted_at ASC, response_id ASC`로 출력한다.
- Response 0건은 같은 canonical header만 있는 CSV로 `200`을 반환한다.

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

`clientSubmissionId`, `payloadHash`, owner/session metadata는 CSV column에 포함하지 않는다. Unknown/unowned/deleted Survey는 다른 Admin Result API와 동일하게 `404 SURVEY_NOT_FOUND`로 conceal한다.

# 6. HTTP and Resource Boundary

```text
GET /api/surveys/{surveyId}/responses/export.csv
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="formdock-survey-{surveyId}-responses.csv"
```

Export는 전체 Survey 범위의 read-only transaction이며 pagination/filter를 적용하지 않는다. 전체 CSV 문자열이나 전체 Answer graph를 한 번에 materialize하지 않고 memory-bounded row/streaming generation을 사용한다.

# 7. Implementation Boundary

Phase 4-C 구현은 owner/non-deleted Survey와 current Question/Option schema를 CSV 첫 byte 전에 확인한다. PostgreSQL `REPEATABLE READ` read-only snapshot이 owner/schema/Response row를 같은 시점에 고정하며, exact `survey_id`의 단일 cursor를 positive fetch size `256`으로 소비하고 현재 Response 한 행만 메모리에 유지한다.

```text
CreatorResponseCsvExportController
→ owner-visible export preparation
→ current Question/Option column schema
→ submitted_at ASC, response_id ASC cursor
→ RFC 4180 OutputStream writer
```

Response, Question 또는 Option별 query loop, 전체 export `String`/`byte[]`, unbounded JPA graph, Product write는 없다. Phase 4-C backend 구현은 완료됐지만 `dev` 통합 전이며 Excel/LibreOffice 실제 application smoke는 Phase 4 Completion evidence가 다시 소유한다.
