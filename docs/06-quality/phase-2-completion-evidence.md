---
title: Phase 2 Survey Builder Completion Evidence
status: active
version: 1.0
last_updated: 2026-08-20
---

# 1. 판정과 경계

최신 merged `dev`의 Phase 2-A→D application tree와 integration validation을 검토한 결과는 다음과 같다.

```text
Phase 0                          COMPLETE
Application Scaffold            COMPLETE
Phase 1 Creator Foundation      COMPLETE + RELEASED
Phase 2 Survey Builder          COMPLETE ON DEV — PENDING RELEASE GATE
Phase 3 Public Survey/Response  NOT AUTHORIZED
Production                      NOT AUTHORIZED

Phase 2 integration evidence    PASS
P0 / P1 / P2 / unresolved       0 / 0 / 0 / 0
```

`COMPLETE ON DEV`는 authenticated Creator Survey Builder capability와 Admin-only Preview가 `dev`에 통합됐다는 뜻이다. `main` release, Phase 3 Public Survey/Response, Result/CSV 또는 Production activation을 뜻하지 않는다. 이 상태의 repository Source of Truth 효력은 completion evidence PR이 `dev`에 merge된 뒤 발생한다.

# 2. Exact Baseline

2026-08-20에 live remote와 Git history에서 확인한 baseline은 다음과 같다.

```text
main SHA       751a9ee33282e20d46f9356ffecfbc110a692c9c
dev SHA        a6a98a3c3fa0adf273d96656159bc60344413bd1
dev tree       2a9816c7cfd3bd4a0419dd46ff40df030169b2a6
main ancestor  PASS
```

PR #31의 final reviewed head `095b42687bba87cd13e34db8b500b74ce92d10ab`도 같은 tree `2a9816c7cfd3bd4a0419dd46ff40df030169b2a6`를 가진다. 따라서 final frontend review와 merged dev application source 사이에 tree drift가 없다.

# 3. Phase 2 Merge Provenance

| Slice | Issue / PR | Reviewed head | `dev` merge | Post-merge `dev` Validate |
|---|---|---|---|---|
| 2-A Survey DRAFT Core | [Issue #24](https://github.com/xxh3898/form-dock/issues/24) / [PR #25](https://github.com/xxh3898/form-dock/pull/25) | `e21edec804149834be9de266f087bdeaec0abae3` | `8cf4dfe91416f93e1b5ced5db8d3917b3e88c594` | [32332097052](https://github.com/xxh3898/form-dock/actions/runs/32332097052) — `success` |
| 2-B Question/Lock Data Foundation | [Issue #26](https://github.com/xxh3898/form-dock/issues/26) / [PR #27](https://github.com/xxh3898/form-dock/pull/27) | `668c639df276b991c304f1619c00c84d6c00b195` | `a48f576f04e92cf80e1440cdbce2df54af429b09` | [32360319569](https://github.com/xxh3898/form-dock/actions/runs/32360319569) — `success` |
| 2-C Builder Backend Completion | [Issue #28](https://github.com/xxh3898/form-dock/issues/28) / [PR #29](https://github.com/xxh3898/form-dock/pull/29) | `4412ac644e006d926a6b7decae8959e184d18207` | `db42afe1d93f4192b924ceb67f8f650c7e0065ef` | [32365920569](https://github.com/xxh3898/form-dock/actions/runs/32365920569) — `success` |
| 2-D Builder Frontend + Preview | [Issue #30](https://github.com/xxh3898/form-dock/issues/30) / [PR #31](https://github.com/xxh3898/form-dock/pull/31) | `095b42687bba87cd13e34db8b500b74ce92d10ab` | `a6a98a3c3fa0adf273d96656159bc60344413bd1` | [32372799517](https://github.com/xxh3898/form-dock/actions/runs/32372799517) — `success` |

네 slice 모두 reviewed head와 merge commit의 tree가 각각 동일하다. GitHub legacy commit-status endpoint는 latest dev에 별도 status를 노출하지 않지만, Checks/Actions source에는 exact `push`, branch `dev`, head `a6a98a3c3fa0adf273d96656159bc60344413bd1`인 run `32372799517`이 확인된다. 따라서 PR run을 post-merge run으로 오인하거나 push evidence가 없다고 추정하지 않는다.

# 4. Immutable Data and Migration Evidence

현재 versioned migration chain은 정확히 다섯 개다.

| Version | File | SHA-256 | Responsibility |
|---|---|---|---|
| V1 | `V1__create_users.sql` | `11e46407f3dbf7c61653f848051053848b7776e9643b3910bc00f109c877b7e1` | Creator/User business schema |
| V2 | `V2__create_spring_session.sql` | `83da1d682414421cacecc942191dd27dc405171b9ca92c03bba571a47937a7f4` | Spring Session JDBC infrastructure |
| V3 | `V3__create_surveys.sql` | `2db4db33f33bf7f22ab6cde4a2153cf6019d3472285f1a99eeb7d3a354ffd9d8` | owner-scoped Survey schema |
| V4 | `V4__create_questions_and_options.sql` | `5471283947e48712f8fe53c26d24a0f7d5d53bca8d22f0034ef95a872e3cdc00` | Question/Option schema and constraints |
| V5 | `V5__create_survey_responses.sql` | `07db184601785853503e48d09c0fbfe8fa8836968e9d02604e39fde4b9bfc846` | schema-only canonical Response identity/lock authority |

V1/V2는 current `main`, V3는 Phase 2-A merge, V4/V5는 Phase 2-B merge의 bytes와 동일하다. V6 또는 다른 새 migration은 없다. Hosted Backend의 clean PostgreSQL 18.6 run에서 Flyway history V1→V5, required tables/indexes/constraints와 application startup이 통과했다.

Phase 2의 V3-V5는 future `main` release에 schema/data impact가 있으므로 Gate 3에서 `RECOVERY PLAN REQUIRED` 여부와 disposable upgrade compatibility를 다시 분류한다. 이 completion gate는 live migration, backup, restore 또는 Production data operation을 실행하지 않았다.

# 5. Integrated Validation

Latest merged dev run [32372799517](https://github.com/xxh3898/form-dock/actions/runs/32372799517)은 exact `dev@a6a98a3c3fa0adf273d96656159bc60344413bd1`의 `push` event다.

| Job | Result | Evidence |
|---|---|---|
| Backend | `SUCCESS` | Java 25, `./gradlew --no-daemon clean check`, 107 total / 107 passed / 0 failed / 0 skipped |
| Frontend | `SUCCESS` | Node 24.19.0, npm install/lint/typecheck, 5 files / 49 tests passed / 0 failed / 0 skipped, production build |
| Infrastructure | `SUCCESS` | Compose config와 existing API/Web Dockerfile image build |
| ARM64 Release Artifact | `SKIPPED` | ordinary `dev` push에 대한 expected policy; Phase 2 release evidence로 재사용하지 않음 |

Backend log에서 `should_runPostgres18_6Alpine3_23Testcontainer_when_testcontainersAreEnabled`가 통과했다. 이 executable contract는 실제 실행 중인 pinned `postgres:18.6-alpine3.23` container와 PostgreSQL major 18을 확인한다. 같은 run에서 clean Flyway V1-V5, session/auth, Survey/Question persistence와 concurrency regression이 모두 실행됐으며 skipped test는 0이다.

Completion audit 중 local validation은 pinned `node:24.19.0-alpine3.24` container에서 `npm ci`, lint, typecheck, 49/49 tests, production build와 audit finding 0을 재확인했다. Local Compose config와 API/Web image build도 통과했다. Host에는 Java runtime을 설치하지 않았고 개발 container에 Docker socket을 mount하지 않는 repository security policy를 지켜 local Testcontainers full check는 실행하지 않았다. Exact merged-dev Hosted Backend가 이를 대체하며, completion PR의 final exact head도 required Hosted jobs를 다시 통과해야 한다.

# 6. Integrated Acceptance Matrix

| Area | Result | Source-grounded evidence |
|---|---|---|
| A. Creator/Auth regression | `PASS` | login/logout/me, JDBC session restart/expiry, Admin 401, unsafe mutation CSRF, arbitrary credentialed CORS absence tests; frontend session guard, memory-only CSRF와 storage-write absence |
| B. Survey CRUD/ownership/lifecycle | `PASS` | owner list/create/detail/PATCH/delete, concealment, reserved slug/race, stale PATCH-delete, DRAFT→OPEN↔CLOSED, first-open timestamp와 current-structure revalidation tests |
| C. Question/Option Builder | `PASS` | six-type create, complete-state update, Option identity, invalid identity/configuration, UNIQUE-safe gapless delete/reorder와 frontend Choice→Choice preservation tests |
| D. First-Response structure lock | `PASS` | Survey `PESSIMISTIC_WRITE` 뒤 real V5 `EXISTS`; seeded canonical Response가 모든 Question mutation을 409로 거절하고 bounded lock wait가 safe 503/partial write 0을 보장 |
| E. Deep duplicate | `PASS` | DRAFT/OPEN/CLOSED/Response-present source deep copy, fresh Survey/Question/Option IDs와 slug, new DRAFT, Response copy 0, count 0, unlocked, failure rollback tests |
| F. Frontend integrated workflow | `PASS` | protected nested routes, list/create/edit/delete/duplicate/open/close, six-type Builder/reorder, canonical response/refetch, stale lock/not-found recovery와 scoped validation errors in 49-test suite |
| G. Admin Preview | `PASS` | authenticated canonical detail, six-type read-only rendering, DRAFT/OPEN/CLOSED-compatible read path, submit/Public request/Response write 0 |
| H. Phase boundary negative scan | `PASS` | no `/s/{slug}` React route, public Survey controller, Product SurveyResponse writer, Answer runtime, Results/CSV or Production activation |

## 6.1 Creator/Auth and Security Boundary

- persistent `User` and server-side Spring Session JDBC remain the Creator identity authorities;
- `/api/auth/login`, `/logout`, `/me` regression and session rotation/restart/expiry behavior pass;
- `/api/**` is authenticated except exact health/auth entry paths and all unsafe Admin requests use CSRF;
- arbitrary credentialed CORS is not configured;
- frontend uses relative same-origin `/api/*`, `credentials: same-origin` and one bounded `CSRF_INVALID` retry;
- CSRF token exists only in client-instance memory; `localStorage`, `sessionStorage` and `document.cookie` writes are absent;
- Product/credential/Secret values were not added by this audit.

## 6.2 Survey, Lifecycle and Ownership Boundary

- list/detail/mutation use owner-scoped active Survey authority and unknown/unowned/deleted identity stays concealed;
- DRAFT/CLOSED soft delete retains the globally reserved slug and OPEN delete is rejected;
- atomic metadata update prevents a stale PATCH from resurrecting a committed soft delete;
- lifecycle is exactly `DRAFT → OPEN ↔ CLOSED`; first OPEN sets `openedAt` once, close sets `closedAt`, reopen preserves `openedAt` and clears `closedAt`;
- OPEN obtains the Survey row lock and validates current persisted Question structure after serialization;
- valid concurrent metadata PATCH remains the separate documented last-commit-wins boundary.

## 6.3 Question Structure and First-Response Lock

All six canonical types are implemented and tested:

```text
SHORT_TEXT
LONG_TEXT
SINGLE_CHOICE
MULTIPLE_CHOICE
SCALE
NUMBER
```

Question create/update/delete/reorder starts inside a caller-owned transaction and calls the shared structure guard:

```text
Survey PESSIMISTIC_WRITE
→ owner / deleted state revalidation
→ real survey_responses EXISTS
→ structure mutation or 409 SURVEY_STRUCTURE_LOCKED
```

There is no `structure_locked` column or denormalized Response count. Product main code exposes only `SurveyResponseReadRepository` COUNT/EXISTS/grouped COUNT reads; `INSERT`, `UPDATE` and `DELETE` Product paths are absent. Direct Response inserts exist only in disposable integration-test fixtures. Lock timeout/deadlock is bounded and maps to `503 TEMPORARILY_UNAVAILABLE` without partial caller writes.

The actual first Public Response insert and mutation-first/submit-first concurrency pair remain Phase 3 work because Phase 2 intentionally has no Product Response writer. Phase 2 completion proves that the already-real V5 authority rejects every structure mutation once a canonical row exists; it does not mark the separate Phase 3 submission acceptance criterion complete.

## 6.4 Frontend and Preview Boundary

Canonical authenticated routes are:

```text
/                                  → /admin
/admin                             → /admin/surveys
/admin/surveys
/admin/surveys/new
/admin/surveys/{surveyId}
/admin/surveys/{surveyId}/preview
```

The shared Admin guard resolves `/api/auth/me` before protected content renders. Mutation response or explicit refetch replaces page-local canonical state. Stale `SURVEY_STRUCTURE_LOCKED` locks structural controls while preserving allowed metadata/Duplicate behavior, and stale `QUESTION_NOT_FOUND` clears the hidden editor selector. Survey metadata and Question `title` errors use separate form-domain state.

Admin Preview uses authenticated Survey detail, renders ordered type-specific configuration and contains no submit handler. Reserved slug is text only; `/s/{slug}` is not a functional route or link.

# 7. Negative Scope and Static Audit

```text
Public /s/{slug} route                  0
Public Survey controller/runtime        0
Product survey_responses writer         0
Answer / AnswerOption runtime            0
payloadHash/idempotency Product flow     0
Result Dashboard                         0
CSV export                               0
new migration                            0
Production/deployment activation         0
```

The schema-only V5 table and direct disposable test fixtures are the accepted ADR-0006 exception. This evidence change contains documentation only: Product source, schema, dependency, lockfile, Docker/Compose runtime and CI workflow diff are 0.

# 8. Review Gate

Current integrated evidence review:

```text
P0            0
P1            0
P2            0
unresolved    0
```

The completion PR must still pass Backend, Frontend and Infrastructure on its final exact head; `ARM64 Release Artifact` must remain expected `SKIPPED`. The final head/run, mergeability and review-thread result belong in the PR body because a commit cannot contain its own future SHA and Hosted run ID.

# 9. Conclusion and Follow-up Ownership

```text
Phase 2 Completion / Integration Evidence  PASS
Phase 2 Survey Builder                     COMPLETE ON DEV — PENDING RELEASE GATE
```

After the user merges this evidence PR and GPT verifies latest merged dev:

1. close Issue #32 as completed;
2. create exactly the Phase 2 Gate 3 main Release Candidate Evidence Issue;
3. validate the full `main...dev` diff, native ARM64 target artifact, disposable PostgreSQL Flyway compatibility and V3-V5 recovery-impact classification;
4. keep the actual `dev → main` release PR separate;
5. keep Phase 3 and Production `NOT AUTHORIZED` until their own gates.
