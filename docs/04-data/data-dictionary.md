---
title: Data Dictionary
status: draft
version: 0.1
last_updated: 2026-08-18
---

# users

- `id`: internal identity
- `email`: Creator login identity
- `password_hash`: password hash only
- `display_name`: Admin UI label
- `role`: V1 `ADMIN`
- timestamps

# surveys

- `owner_id`: Creator
- `title`: display title
- `description`: respondent intro
- `slug`: public identity
- `privacy_notice`: optional notice
- `status`: DRAFT/OPEN/CLOSED
- `opened_at`, `closed_at`
- `deleted_at`
- timestamps

# questions

- common metadata
- type-specific scale/number settings
- unique position per Survey

# question_options

- Choice label
- position

# survey_responses

- `survey_id`
- `client_submission_id`
- `payload_hash`
- `submitted_at`

# answers

- `response_id`
- `question_id`
- `text_value`
- `numeric_value`

Question type에 따라 하나의 representation만 사용.

# answer_options

Choice answers selected options.

# Open Decisions

- exact varchar lengths
- email case normalization
- CSV formatting
- retention
