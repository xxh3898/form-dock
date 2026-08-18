---
title: Respondent Requirements
status: draft
version: 0.1
last_updated: 2026-08-18
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

# 6. Closed/Invalid Survey

- Public GET은 OPEN + not deleted Survey만 반환하며 DRAFT/CLOSED/deleted/unknown slug는 모두 `404 Not Found`다.
- 이미 화면을 연 뒤 Survey가 CLOSED가 된 신규 submit은 `409 Conflict` / `SURVEY_NOT_OPEN`이다. CLOSED 이전에 생성된 canonical Response의 동일 replay는 `200 OK`다.
- DRAFT/deleted/unknown slug로 submit하면 `404 Not Found`다.

UI는 404에서 unavailable 화면을, 409 `SURVEY_NOT_OPEN`에서 더 이상 제출할 수 없다는 화면을 표시한다. 사용자 문구의 세부 copy는 UI 구현에서 조정할 수 있으나 상태 의미는 바꾸지 않는다.

# 7. Accessibility

- keyboard navigation
- visible focus
- associated labels
- error text
- 충분한 touch target
