---
title: ADR-0003 Open Survey Mutation Policy
status: draft
version: 0.1
last_updated: 2026-08-18
---

# Status

`proposed`

# Context

응답이 이미 저장된 Survey의 Question/Option을 변경하면 과거 Response 의미가 바뀔 수 있다.

# Options

1. 언제든 구조 수정
2. Survey Versioning
3. 첫 Response 이후 구조 immutable

# Decision

추천:

```text
첫 canonical Response 이후 Question semantics immutable
```

# Rationale

- V1 complexity 최소화
- Answer snapshot/version table 불필요
- historical meaning 안정
- duplicate-to-new-survey workflow로 대체 가능

# Consequences

- 운영 중 구조 오타 수정 제약
- 변경 필요 시 Survey duplicate
- future requirement가 생기면 versioning ADR 재검토

# Lock Authority

```text
responseCount > 0
```

별도 `structure_locked` column은 두지 않는다.
