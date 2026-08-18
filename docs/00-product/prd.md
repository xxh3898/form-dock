---
title: FormDock Product Requirements Document
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Summary

FormDock은 승인된 Creator가 설문을 생성·운영하고, 비로그인 Respondent가 공개 URL로 응답하며, Creator가 결과를 조회하고 CSV로 내보낼 수 있는 서비스다.

# 2. Primary User Stories

## Creator

- 설문 생성/수정/복제
- 질문 추가/수정/삭제/순서 변경
- 미리보기
- 설문 공개/종료
- 응답 조회
- CSV Export

## Respondent

- 로그인 없이 공개 설문 접속
- 모바일에서 단계별 응답
- 진행률 확인
- 검증 오류 수정
- 최종 제출

# 3. Survey Lifecycle

```text
DRAFT → OPEN ↔ CLOSED
```

- `DRAFT`: 공개 불가, 자유 편집
- `OPEN`: 공개/응답 가능
- `CLOSED`: 신규 응답 불가, 기존 결과 조회 가능

# 4. Question Types

```text
SHORT_TEXT
LONG_TEXT
SINGLE_CHOICE
MULTIPLE_CHOICE
SCALE
NUMBER
```

# 5. Mutation Policy

첫 canonical Response 이전에는 Survey 구조를 수정할 수 있다.

첫 canonical Response 이후에는 기존 응답의 의미를 훼손할 수 있는 구조 변경을 금지한다.

구조 변경이 필요하면 기존 Survey를 복제하여 새 DRAFT Survey를 만든다.

# 6. Public Survey UX

```text
Survey Intro
→ Question
→ Question
→ ...
→ Submit
→ Completion
```

V1 기본 렌더링은 `STEP_BY_STEP`이다.

별도의 50% 완료 페이지는 사용하지 않고 progress indicator만 제공한다.

# 7. Response Submission

Backend는 반드시 다음을 검증한다.

- Survey 존재/상태
- deleted 여부
- required
- answer type
- option ownership
- scale/number range
- text length
- clientSubmissionId

# 8. Duplicate Submission

목적은 악의적 다중응답 완전 차단이 아니라 더블클릭/네트워크 재시도 중복 방지다.

V1 기본 방향:

```text
clientSubmissionId UUID
+
server-side idempotency
```

IP 기반 강제 중복 차단은 하지 않는다.

# 9. Results

- 응답 수
- 최근 응답 시각
- 객관식 분포
- Scale 평균/분포
- Text/Number 답변
- 개별 Response 조회

# 10. CSV

Survey 단위 전체 Response를 UTF-8 CSV로 다운로드한다.

MULTIPLE_CHOICE 직렬화 방식은 `TBD`.

# 11. Authentication

Creator 영역:

```text
Spring Security
Server-side session
HttpOnly
Secure
SameSite
```

JWT는 V1 기본 설계에서 제외한다.

# 12. Privacy

FormDock 자체는 이름/이메일을 자동 수집하지 않는다.

Creator가 질문으로 직접 요구하는 경우에만 응답 데이터에 포함된다.

선택적 `privacyNotice`를 지원한다.

# 13. Technical Baseline

```text
Java 25
Spring Boot 4
Gradle
React
TypeScript
Vite
PostgreSQL 18
Flyway
Docker Compose
Mac mini
Cloudflare Tunnel
```

# 14. V1 Success Criteria

- Creator 로그인
- Survey CRUD
- 6개 Question type
- Preview
- OPEN/CLOSED
- Public response
- 서버 검증
- 기본 idempotency
- Result dashboard
- CSV
- Docker Compose
- Mac mini production
- backup/restore 절차
