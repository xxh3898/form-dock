---
title: V1 Acceptance Criteria
status: draft
version: 0.1
last_updated: 2026-08-18
---

# Creator

- [ ] 승인된 계정 로그인 가능
- [ ] API container restart 후 JDBC-backed Creator session 유지
- [ ] login/logout/Admin mutation CSRF 보호
- [ ] one-time bootstrap이 secret을 저장/log하지 않고 duplicate user를 만들지 않음
- [ ] 타 Creator Survey 접근 차단
- [ ] Survey create/edit/duplicate
- [ ] DRAFT/OPEN/CLOSED 동작
- [ ] 6개 Question type
- [ ] structure lock
- [ ] 첫 Response와 structure mutation concurrency에서 둘 다 commit되지 않음
- [ ] first OPEN 이후 slug immutable
- [ ] OPEN direct delete 거절

# Respondent

- [ ] 비로그인 OPEN Survey 접근
- [ ] 모바일 단계별 응답
- [ ] progress
- [ ] required/type validation
- [ ] atomic submit
- [ ] retry duplicate 방지
- [ ] same payload replay 200 / conflicting replay 409
- [ ] CLOSED 신규 submit 409 / 기존 동일 replay 200, unavailable public GET 404
- [ ] completion

# Results

- [ ] total count
- [ ] individual response
- [ ] choice summary
- [ ] scale summary
- [ ] text/number display
- [ ] CSV
- [ ] MULTIPLE_CHOICE option별 boolean column
- [ ] CSV formula injection 방어

# Data

- [ ] Flyway clean install
- [ ] PostgreSQL constraints
- [ ] no DB public exposure
- [ ] backup
- [ ] restore verification

# Operations

- [ ] ARM64 images
- [ ] Compose health
- [ ] Mac mini deploy
- [ ] Cloudflare route
- [ ] public smoke

# Dogfooding

- [ ] real survey created
- [ ] real external responses collected
- [ ] exported data used for actual analysis
