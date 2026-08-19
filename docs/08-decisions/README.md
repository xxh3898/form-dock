---
title: Architecture Decision Records
status: draft
version: 0.2
last_updated: 2026-08-19
---

# ADR Convention

중요하고 되돌리기 어려운 결정을 ADR로 기록한다.

## Status

```text
proposed
accepted
superseded
deprecated
```

## Naming

```text
adr-0001-*.md
```

## Current ADRs

| ADR | Decision | Status |
|---|---|---|
| [ADR-0001](adr-0001-authentication.md) | Creator Authentication | accepted |
| [ADR-0002](adr-0002-survey-response-model.md) | Survey Response Model | accepted |
| [ADR-0003](adr-0003-open-survey-mutation-policy.md) | Open Survey Mutation Policy | accepted |
| [ADR-0004](adr-0004-survey-structure-concurrency.md) | Survey Structure Concurrency | accepted |
| [ADR-0005](adr-0005-release-and-production-gate-separation.md) | Release and Production Gate Separation | accepted |

ADR은 명시된 decision scope에서만 authority를 가진다. Product scope를 변경하거나 다른 영역의 세부 contract를 중복 소유하지 않는다.
