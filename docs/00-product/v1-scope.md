---
title: FormDock V1 Scope
status: draft
version: 0.2
last_updated: 2026-08-20
---

# 1. Objective

V1은 승인된 Creator가 실제 프로젝트에서 반복 사용할 수 있는 최소 Survey Builder + Response Collection 플랫폼을 production에 제공하는 단계다.

# 2. In Scope

## Authentication

- Login
- Logout
- Current user
- Private Creator account

## Survey

- Create
- Read
- Update
- Soft Delete
- Duplicate
- Preview
- OPEN
- CLOSED

## Questions

- SHORT_TEXT
- LONG_TEXT
- SINGLE_CHOICE
- MULTIPLE_CHOICE
- SCALE
- NUMBER
- required
- description
- position
- options

## Public Survey

- `/s/{slug}`
- anonymous access
- step-by-step
- progress
- client/server validation
- completion

## Response

- atomic submit
- Answer/AnswerOption
- required/type validation
- basic idempotency

## Results

- total count
- question summary
- individual response
- CSV

## Operations

- Docker Compose
- ARM64 compatible images
- PostgreSQL
- health checks
- Cloudflare Tunnel
- backup/restore
- CI validation

# 3. Out of Scope

- Public signup
- Password reset automation
- OAuth
- Subscription/payment
- Workspace/team
- Enterprise RBAC
- Conditional logic
- Branching
- File upload
- Quiz/score
- Matrix
- Randomization
- Email/SMS/Push
- AI generation/analysis
- Advanced statistics
- Real-time collaboration
- Marketplace
- Native mobile app

# 4. Screen Scope

```text
/login
/admin/surveys
/admin/surveys/new
/admin/surveys/{id}
/admin/surveys/{id}/preview
/admin/surveys/{id}/responses
/admin/surveys/{id}/responses/{responseId}

/s/{slug}
/s/{slug}/complete
```

# 5. V1 Exit

```text
Core feature complete
Automated tests pass
API documented
ARM64 build pass
Mac mini deploy pass
Backup pass
Restore verified
Public smoke pass
Real survey dogfooding pass
```
