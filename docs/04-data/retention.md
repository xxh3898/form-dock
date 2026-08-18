---
title: Data Retention Policy
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Current Status

Live retention의 정확한 기간은 post-dogfooding data volume과 privacy review까지 명시적으로 deferred한다. 결정 owner는 이 문서이며 application scaffold를 막지 않는다.

# 2. Principles

- 필요한 데이터만 저장
- Survey soft delete 시 Response 즉시 삭제하지 않음
- automatic purge는 V1 기본 기능 아님
- backup과 live retention을 구분
- soft-deleted Survey Response는 V1 일반 Admin/Public API에서 접근하지 않지만 live DB에 보존

# 3. Respondent Tracking

Persistent respondent tracking identifier 저장 안 함.

# 4. Personal Data

FormDock이 자동으로 name/email을 수집하지 않는다.

Creator가 질문으로 수집한 개인정보는 해당 Survey의 목적과 정책에 따라 다뤄야 한다.

# 5. Account Deletion

Public signup이 없으므로 Creator account deletion은 운영자 관리 절차로 다룬다.

# 6. Deferred Decisions

- Survey purge window
- Response purge
- Creator removal
- backup retention
- privacy notice templates

위 항목은 post-dogfooding privacy/operations review에서 결정한다. V1에는 automatic purge job이나 restore UI를 추가하지 않는다.
