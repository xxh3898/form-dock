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

- DRAFT: unavailable
- CLOSED: submission unavailable
- deleted/not found: unavailable

구체 HTTP/UI 문구는 API/UX 구현에서 확정한다.

# 7. Accessibility

- keyboard navigation
- visible focus
- associated labels
- error text
- 충분한 touch target
