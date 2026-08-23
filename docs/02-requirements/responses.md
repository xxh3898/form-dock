---
title: Response Management Requirements
status: draft
version: 0.4
last_updated: 2026-08-23
---

# 1. Overview

Creator는 자신의 Survey Response만 조회할 수 있다.

# 2. Overview Data

- total response count
- last submittedAt
- status
- question count

Response 0건에서도 overview는 정상 결과이며 total count는 `0`, last `submittedAt`은 `null`이다.

# 3. Question Summary

## Choice

- Option position 순서의 count
- 해당 Question `answeredCount`를 denominator로 한 percentage
- percentage는 scale 2 `HALF_UP` decimal string
- no answers이면 `0.00`
- MULTIPLE_CHOICE percentage 합계는 100%를 초과할 수 있음

## Scale

- answered count
- scale 2 `HALF_UP` average; no answers이면 `null`
- configured min..max 전체 integer bucket distribution
- zero-count bucket 포함, percentage는 answered count denominator와 scale 2 `HALF_UP`

## Text

- summary에는 answered count만 제공
- raw text는 bounded Response list/detail로 조회

## Number

- summary에는 answered count만 제공
- raw canonical decimal은 bounded Response list/detail로 조회

NUMBER average와 고급 집계는 V1 dogfooding 이후 결과 UX 검토로 deferred한다. 이 결정은 application scaffold와 기본 count/raw value 구현을 막지 않는다.

# 4. Individual Response

Current Survey Question 전체를 Question `position ASC`로 보여준다. Optional unanswered Question도 포함하고 `answer=null`로 표현한다.

- text는 stored exact decoded text다.
- SCALE/NUMBER는 exponent 없는 canonical plain decimal string이며 zero는 `0`이다.
- Choice는 selected Option만 Option `position ASC`로 제공한다.
- `clientSubmissionId`와 `payloadHash`는 Product Result DTO에 노출하지 않는다.

# 5. Mutation

V1에서는 Response edit/delete/exclude 미지원.

# 6. Pagination

응답 수가 증가할 수 있으므로 individual response list는 서버 pagination을 사용한다.

```text
page default/minimum  0 / 0
size default/range    50 / 1..100
sort                  submittedAt DESC, responseId DESC
```

Invalid page/size는 `400 VALIDATION_FAILED`, 존재하는 Survey의 범위를 넘는 page는 `200`과 empty items다. User-selected sort/filter/search는 V1 범위 밖이다.

# 7. Ownership and Query Boundary

- Creator session과 owner-scoped non-deleted Survey read가 선행 authority다.
- unknown/unowned/deleted Survey는 `404 SURVEY_NOT_FOUND`로 conceal한다.
- unknown 또는 exact owned Survey에 속하지 않는 Response는 `404 RESPONSE_NOT_FOUND`다.
- DRAFT/OPEN/CLOSED lifecycle은 owner Result read를 막지 않는다.
- summary는 unbounded raw Answer array를 반환하지 않고 database grouped aggregation을 우선한다.

Phase 4-A list/detail, Phase 4-B owner-first summary와 Phase 4-C memory-bounded CSV export는 `dev`에 통합됐다. Phase 4-D Results frontend는 이 authority를 변경하지 않고 Admin overview/summary/list/detail/CSV flow를 구현했으며 `dev` 통합 대기 상태다.
