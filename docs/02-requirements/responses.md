---
title: Response Management Requirements
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Overview

Creator는 자신의 Survey Response만 조회할 수 있다.

# 2. Overview Data

- total response count
- last submittedAt
- status
- question count

# 3. Question Summary

## Choice

- option count
- percentage

## Scale

- count
- average
- distribution

## Text

- response list

## Number

- count
- average 후보
- raw values

정확한 집계 항목은 구현 전 최종 조정 가능.

# 4. Individual Response

Question order 기준으로 제출 내용을 보여준다.

# 5. Mutation

V1에서는 Response edit/delete/exclude 미지원.

# 6. Pagination

응답 수가 증가할 수 있으므로 individual response list는 서버 pagination 사용을 권장한다.
