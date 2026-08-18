---
title: FormDock Response Domain Model
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Aggregate

```text
SurveyResponse
└─ Answer
   └─ AnswerOption
```

V1 Response Draft는 없다.

# 2. SurveyResponse

```text
id
surveyId
clientSubmissionId
payloadHash
submittedAt
```

# 3. Idempotency

```text
UNIQUE(survey_id, client_submission_id)
```

- 첫 request: create
- same id + same payload: canonical Response로 수렴
- same id + different payload: reject

`payloadHash`는 서버가 canonical payload로 계산한다.

# 4. No Persistent Respondent Token

V1은 동일 브라우저 장기 추적용 token을 저장하지 않는다.

악의적 다중응답 방지는 Non-goal이다.

# 5. Atomic Submission

```text
Survey validation
→ Question/Answer validation
→ idempotency
→ SurveyResponse
→ Answer
→ AnswerOption
→ COMMIT
```

실패 시 rollback.

# 6. Answer

```text
id
responseId
questionId
textValue
numericValue
createdAt
```

```text
UNIQUE(response_id, question_id)
```

Optional question unanswered는 Answer row 자체를 만들지 않는다.

# 7. Value Mapping

| Type | text | numeric | option |
|---|---|---|---|
| SHORT_TEXT | YES | NO | NO |
| LONG_TEXT | YES | NO | NO |
| SINGLE_CHOICE | NO | NO | YES |
| MULTIPLE_CHOICE | NO | NO | YES |
| SCALE | NO | YES | NO |
| NUMBER | NO | YES | NO |

# 8. Mutation

Respondent는 제출 후 수정/삭제 불가.

Creator도 V1에서 원문 수정 불가.

# 9. Invariants

- OPEN Survey만 신규 Response
- transaction atomic
- Question당 최대 1 Answer
- Option ownership 검증
- idempotent retry
- persistent tracking identifier 없음
