---
title: Admin API Contract
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Prefix

```text
/api
```

# 2. Auth

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

# 3. Surveys

후보:

```text
GET    /api/surveys
POST   /api/surveys
GET    /api/surveys/{surveyId}
PATCH  /api/surveys/{surveyId}
DELETE /api/surveys/{surveyId}

POST   /api/surveys/{surveyId}/duplicate
POST   /api/surveys/{surveyId}/open
POST   /api/surveys/{surveyId}/close
```

# 4. Questions

```text
POST   /api/surveys/{surveyId}/questions
PATCH  /api/surveys/{surveyId}/questions/{questionId}
DELETE /api/surveys/{surveyId}/questions/{questionId}
POST   /api/surveys/{surveyId}/questions/reorder
```

QuestionOption은 Question mutation payload 안에 포함하는 방식과 별도 endpoint를 구현 시 비교한다.

# 5. Responses

```text
GET /api/surveys/{surveyId}/responses
GET /api/surveys/{surveyId}/responses/{responseId}
GET /api/surveys/{surveyId}/responses/summary
GET /api/surveys/{surveyId}/responses/export.csv
```

# 6. Authorization

모든 Admin Survey API는 owner check 필수.

# 7. Documentation

Spring REST Docs로 구현된 contract를 검증한다.
