---
title: FormDock ERD
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Core Tables

```text
users
surveys
questions
question_options
survey_responses
answers
answer_options
```

# 2. ERD

```mermaid
erDiagram
    USERS ||--o{ SURVEYS : owns
    SURVEYS ||--o{ QUESTIONS : contains
    QUESTIONS ||--o{ QUESTION_OPTIONS : has
    SURVEYS ||--o{ SURVEY_RESPONSES : receives
    SURVEY_RESPONSES ||--o{ ANSWERS : contains
    QUESTIONS ||--o{ ANSWERS : answered_by
    ANSWERS ||--o{ ANSWER_OPTIONS : selects
    QUESTION_OPTIONS ||--o{ ANSWER_OPTIONS : selected
```

# 3. Key Constraints

```text
UNIQUE(users.email)
UNIQUE(surveys.slug)
UNIQUE(questions.survey_id, questions.position)
UNIQUE(question_options.question_id, question_options.position)
UNIQUE(survey_responses.survey_id, survey_responses.client_submission_id)
UNIQUE(answers.response_id, answers.question_id)
PRIMARY KEY(answer_options.answer_id, answer_options.option_id)
```

추가 CHECK boundary:

- Survey status는 `DRAFT`, `OPEN`, `CLOSED` 중 하나다.
- SCALE 설정은 integer이며 `1 <= min < max <= 10`이다.
- NUMBER min/max가 모두 있으면 `min <= max`다.
- Answer row는 text와 numeric을 동시에 저장하지 않는다.

Choice Option 최소 개수, Answer와 Question type의 정확한 representation 일치, Option ownership 같은 cross-row invariant는 application transaction에서 검증한다.

# 4. Identifier Policy

- Core internal IDs: BIGINT IDENTITY
- clientSubmissionId: UUID
- Public Survey: slug
- payloadHash: SHA-256 lowercase hex

# 5. Timestamp

DB: `TIMESTAMPTZ`

Java: `Instant`
