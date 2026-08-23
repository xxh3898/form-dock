---
title: Respondent Requirements
status: draft
version: 0.3
last_updated: 2026-08-23
---

# 1. Entry

Respondent는 `/s/{slug}`로 로그인 없이 접근한다.

# 2. Mobile-first

최소 360px 폭에서 정상 사용 가능해야 한다.

# 3. Flow

```text
Intro
→ Step-by-step Questions
→ Submit
→ Completion
```

# 4. Progress

상단 progress indicator를 사용한다.

중간에 별도 완료율 페이지를 띄우지 않는다.

# 5. Validation

Frontend에서 즉시 피드백하되 서버 validation이 최종 authority다.

- unknown/foreign/duplicate Question ID를 거절한다.
- required Question은 type에 맞는 Answer 하나를 요구하고 optional unanswered Question은 생략한다.
- SHORT_TEXT는 500, LONG_TEXT는 5000 Unicode code points 이하이며 blank-only text는 거절한다. Accepted text는 trim/normalize하지 않는다.
- SINGLE_CHOICE는 owned Option 하나, MULTIPLE_CHOICE는 distinct owned Option 하나 이상을 요구한다.
- SCALE은 configured inclusive range의 base-10 integer string, NUMBER는 exponent 없는 `NUMERIC(19,4)` 범위 decimal string을 사용한다.
- unused value, empty Option list와 duplicate Option ID를 거절한다.

# 6. Closed/Invalid Survey

- Public GET은 OPEN + not deleted Survey만 반환하며 DRAFT/CLOSED/deleted/unknown slug는 모두 `404 Not Found`다.
- 이미 화면을 연 뒤 Survey가 CLOSED가 된 신규 submit은 `409 Conflict` / `SURVEY_NOT_OPEN`이다. CLOSED 이전에 생성된 canonical Response의 동일 replay는 `200 OK`다.
- DRAFT/deleted/unknown slug로 submit하면 `404 Not Found`다.

UI는 404에서 unavailable 화면을, 409 `SURVEY_NOT_OPEN`에서 더 이상 제출할 수 없다는 화면을 표시한다. 사용자 문구의 세부 copy는 UI 구현에서 조정할 수 있으나 상태 의미는 바꾸지 않는다.

# 7. Retry and Transport

- 현재 in-memory form/submission attempt마다 UUID `clientSubmissionId` 하나를 만든다.
- network/transient failure와 결과가 불확실한 submit retry는 같은 UUID를 재사용한다. 한 번 실패했다는 이유만으로 새 UUID를 만들지 않는다.
- page reload 또는 새 form instance는 새 UUID를 만들 수 있다.
- UUID를 localStorage, sessionStorage, cookie 또는 DB respondent token으로 저장하지 않는다.
- raw JSON request body가 1 MiB(1,048,576 bytes)를 넘으면 `413 RESPONSE_PAYLOAD_TOO_LARGE`다.
- `429 RATE_LIMITED`와 `503 TEMPORARILY_UNAVAILABLE`는 동일 UUID로 안전하게 retry 가능한 상태를 제공한다.

# 8. Accessibility

- keyboard navigation
- visible focus
- associated labels
- error text
- 충분한 touch target

# 9. Phase Boundary

Phase 3는 `/s/:slug`의 public collection과 completion까지만 소유한다. Creator Result list/detail, summary와 CSV는 Phase 4의 별도 Admin 구현 범위이며 respondent Response read/edit/delete는 계속 제공하지 않는다.
