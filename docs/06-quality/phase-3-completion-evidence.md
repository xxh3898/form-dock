---
title: Phase 3 Public Survey and Response Completion Evidence
status: active
version: 1.0
last_updated: 2026-08-22
---

# 1. 판정과 경계

최신 merged `dev`의 Phase 3-A→D application tree와 integration validation을 검토한 결과는 다음과 같다.

```text
Phase 0                          COMPLETE
Application Scaffold            COMPLETE
Phase 1 Creator Foundation      COMPLETE + RELEASED
Phase 2 Survey Builder          COMPLETE + RELEASED
Phase 3 Public Survey/Response  COMPLETE ON DEV — PENDING RELEASE GATE
Phase 4 Results / Export        NOT AUTHORIZED
Production                      NOT AUTHORIZED

Phase 3 integration evidence    PASS
P0 / P1 / P2 / unresolved       0 / 0 / 0 / 0
```

`COMPLETE ON DEV`는 anonymous Public Survey read, atomic Response submit과 respondent frontend가 `dev`에 통합됐다는 뜻이다. `main` release, Phase 4 Result/CSV, Release tag 또는 Production activation을 뜻하지 않는다. 이 상태의 repository Source of Truth 효력은 completion evidence PR이 `dev`에 merge된 뒤 발생한다.

# 2. Exact Baseline

2026-08-21에 live remote와 Git history에서 확인한 baseline은 다음과 같다.

```text
main SHA       5f7b00384c840203edbfb689f05ed0abe4737b43
main tree      f9439c9807505103e431e113bdc2c966bc701d3e
dev SHA        fefc7b65d36c1aef7c1560e19379ee2ec693baf6
dev tree       cd04a4e0b2dbdef742b8e74fcfedb9feffaf903d
merge-base     5f7b00384c840203edbfb689f05ed0abe4737b43
main ancestor  PASS
```

Current `dev`는 Phase 3-D integration commit 뒤 PR #54의 release tag policy 문서만 추가한 tree다. PR #54는 Product source, migration, dependency, CI와 Docker runtime을 변경하지 않았다.

# 3. Phase 3 Integration Provenance

| Slice | Issue / PR | Final reviewed head | Reviewed tree | `dev` integration | Integration tree | Match |
|---|---|---|---|---|---|---|
| 3-A Public Survey Read | [#45](https://github.com/xxh3898/form-dock/issues/45) / [#46](https://github.com/xxh3898/form-dock/pull/46) | `7865866a3a3700cff4366ec377d037db42afb709` | `e402eb0320de2960e1b5ef85de77091c01711f2a` | `1105bdad999f522c92b68bc2861c285dbed96d89` | `e402eb0320de2960e1b5ef85de77091c01711f2a` | `PASS` |
| 3-B Response Data & Canonicalization | [#47](https://github.com/xxh3898/form-dock/issues/47) / [#48](https://github.com/xxh3898/form-dock/pull/48) | `d443cf311fc53923db581aeee4bf81fcfe681ee6` | `85906c5a09c414fb2d0dda332cd53c9019533565` | `2b76f77cbef868b30554e85abc47f8b0aa549174` | `85906c5a09c414fb2d0dda332cd53c9019533565` | `PASS` |
| 3-C Atomic Public Submission | [#49](https://github.com/xxh3898/form-dock/issues/49) / [#50](https://github.com/xxh3898/form-dock/pull/50) | `d18744741115dc10ab6190264c040084e18bc8f1` | `626fa37bab622b26702784b9ca6a40278ff7cc7f` | `ba96b96e728d36def3e482e58de13c2df8025034` | `626fa37bab622b26702784b9ca6a40278ff7cc7f` | `PASS` |
| 3-D Respondent Frontend | [#51](https://github.com/xxh3898/form-dock/issues/51) / [#52](https://github.com/xxh3898/form-dock/pull/52) | `12f47418b3d7bf67a26d28b43b20a45c67f1a56a` | `a83bf31b8d23aa4b01a6c094c90e33ee7a01b47a` | `7a53a72044e81453676ae08a783f070531091b4d` | `a83bf31b8d23aa4b01a6c094c90e33ee7a01b47a` | `PASS` |

GitHub의 네 integration commit은 각각 이전 `dev` 한 개를 parent로 가진 squash integration이며, reviewed head 자체를 ancestry로 보존하지 않는다. 대신 각 final reviewed tree와 integration tree가 byte-for-byte 동일하고 네 integration commit 모두 current `dev` ancestry에 있다.

## 3.1 Post-Phase 3-D governance change

| PR | Reviewed head/tree | `dev` integration/tree | Diff | Runtime impact |
|---|---|---|---|---|
| [#54](https://github.com/xxh3898/form-dock/pull/54) | `77c28ebdbcfeec0d29528a7d9066aed1d6a31974` / `cd04a4e0b2dbdef742b8e74fcfedb9feffaf903d` | `fefc7b65d36c1aef7c1560e19379ee2ec693baf6` / `cd04a4e0b2dbdef742b8e74fcfedb9feffaf903d` | `docs/07-operations/deployment-runbook.md` 한 파일 | `NONE` |

PR #54는 release tag 정책만 문서화하므로 Phase 3 runtime provenance와 분리한다.

# 4. Immutable Migration and Schema Evidence

현재 versioned migration chain은 정확히 여섯 개다. SHA-256은 current file bytes에서 직접 계산했다.

| Version | File | SHA-256 | Responsibility |
|---|---|---|---|
| V1 | `V1__create_users.sql` | `11e46407f3dbf7c61653f848051053848b7776e9643b3910bc00f109c877b7e1` | Creator/User schema |
| V2 | `V2__create_spring_session.sql` | `83da1d682414421cacecc942191dd27dc405171b9ca92c03bba571a47937a7f4` | Spring Session JDBC infrastructure |
| V3 | `V3__create_surveys.sql` | `2db4db33f33bf7f22ab6cde4a2153cf6019d3472285f1a99eeb7d3a354ffd9d8` | owner-scoped Survey schema |
| V4 | `V4__create_questions_and_options.sql` | `5471283947e48712f8fe53c26d24a0f7d5d53bca8d22f0034ef95a872e3cdc00` | Question/Option schema |
| V5 | `V5__create_survey_responses.sql` | `07db184601785853503e48d09c0fbfe8fa8836968e9d02604e39fde4b9bfc846` | canonical Response identity/lock authority |
| V6 | `V6__create_answers_and_answer_options.sql` | `199e85e2c5111a65cb664607c433184a4eb6815650bf95e4b34ecd1b8dfda752` | Answer/AnswerOption schema |

V1~V5는 Phase 3-B 직전 `dev@1105bdad999f522c92b68bc2861c285dbed96d89`의 bytes와 각각 동일하다. V6는 `answers`와 `answer_options` 두 table만 생성하며 기존 V5 `survey_responses` 정의를 변경하지 않는다. V7은 없다.

Static schema/application audit 결과 persistent `structure_locked`, denormalized Response count와 second Response identity authority는 없다. `responseCount`와 `structureLocked`는 canonical `survey_responses`의 derived COUNT/EXISTS다.

# 5. Integrated Validation

Latest merged dev run [32492530825](https://github.com/xxh3898/form-dock/actions/runs/32492530825)은 exact `dev@fefc7b65d36c1aef7c1560e19379ee2ec693baf6`의 `push` event다.

| Job | Result | Evidence |
|---|---|---|
| Backend | `SUCCESS` | Temurin Java 25.0.4, Gradle 9.7.0, `./gradlew --no-daemon clean check`, 151 total / 151 passed / 0 failed / 0 skipped |
| Frontend | `SUCCESS` | Node 24.19.0, npm 11.17.0, lint/typecheck, 9 files / 86 tests passed / 0 failed / 0 skipped, Vite production build |
| Infrastructure | `SUCCESS` | Compose config와 API/Web Dockerfile image build |
| ARM64 Release Artifact | `SKIPPED` | ordinary `dev` push의 expected policy; Gate 3 ARM64 release evidence로 사용하지 않음 |

Backend의 `FormDockApplicationIntegrationTest.should_runPostgres18_6Alpine3_23Testcontainer_when_testcontainersAreEnabled`는 실제 실행 중인 `postgres:18.6-alpine3.23` Testcontainer와 PostgreSQL major 18을 검증한다. 같은 class의 migration test와 `DatabaseMigrationIntegrationTest`는 clean Flyway V1→V6 및 table/index/constraint를 검증한다.

로컬에서는 pinned `node:24.19.0-alpine3.24` container로 `npm ci`, lint, typecheck, 86/86 tests, production build와 dependency audit finding 0을 재확인했다. `docker compose --env-file .env.example -f infra/compose.yaml config --quiet`도 통과했다. Host에는 Java runtime을 설치하지 않았고 개발 container에 Docker socket을 mount하지 않는 security policy를 지켜 local Testcontainers full check는 실행하지 않았다. Exact merged-dev Hosted Backend가 baseline authority이며 completion PR final exact head도 required Hosted jobs를 다시 통과해야 한다.

# 6. Integrated Acceptance Matrix

| Area | Result | Source-grounded evidence |
|---|---|---|
| A. Creator/Auth regression | `PASS` | `CreatorAuthenticationIntegrationTest`의 login/logout/me, JDBC restart/expiry, Admin 401, unsafe CSRF와 CORS absence; `App.test.tsx`의 Admin guard/login/logout regression |
| B. Public Survey read + concealment | `PASS` | `PublicSurveyApiIntegrationTest.should_returnRespondentSafeOrderedSurvey_when_openSurveyIsRequestedAnonymously`, unavailable-state identical 404와 exact GET security tests |
| C. V6 Response data + canonicalization | `PASS` | `DatabaseMigrationIntegrationTest` V5 preservation/V6 constraints, `ResponsePayloadCanonicalizerTest` literal JSON/hash/order/numeric/transport vectors, `ResponsePersistenceIntegrationTest` aggregate/idempotency/race/rollback tests |
| D. Public atomic submission + idempotency/lifecycle | `PASS` | `PublicResponseApiIntegrationTest`의 first 201/replay 200/conflict 409, unavailable/CLOSED ordering, six-type/required/boundary와 partial-write 0 tests |
| E. Same-Survey concurrency / structure lock | `PASS` | `PublicResponseConcurrencyIntegrationTest`의 mutation-first, submit-first, duplicate race와 timeout/same-ID retry; `SurveyStructureGuardIntegrationTest`의 real EXISTS/503 rollback |
| F. Transport + security boundary | `PASS` | `PublicResponseApiIntegrationTest`의 exact POST CSRF exemption, 1 MiB/+1 raw boundary와 415; `PublicResponseRateLimitIntegrationTest`의 direct-peer 429/forwarded-header ignore |
| G. Respondent frontend vertical flow | `PASS` | `PublicSurveyPage.test.tsx` 18 tests, `publicSurveyClient.test.ts`, `publicResponseForm.test.ts`, `App.test.tsx`; six-type/zero-question/retry/pending/cross-slug/a11y route evidence |
| H. Phase boundary negative scan | `PASS` | endpoint/route/migration/static scan에서 Phase 4 Result/CSV, Public Response read/edit/delete, V7, Production/tag/deploy runtime 0 |

## 6.1 Creator/Auth regression

- server-side Spring Session JDBC가 Creator identity authority이며 restart/expiry/invalidation regression이 통과한다.
- anonymous Admin API는 401이고 login/logout/Admin unsafe mutation은 CSRF를 요구한다.
- arbitrary credentialed CORS allow는 없으며 Public respondent route도 Admin session을 authority로 사용하지 않는다.

## 6.2 Public read and Response lifecycle

- exact anonymous GET은 OPEN + not-deleted Survey만 respondent-safe ordered six-type DTO로 반환한다.
- DRAFT/CLOSED/deleted/unknown GET은 동일 `404 SURVEY_NOT_FOUND`로 conceal한다.
- first Response는 201, same canonical identity/payload replay는 200, different payload는 409다.
- DRAFT/deleted/unknown submit은 404다. CLOSED existing same/different replay는 200/409이고 new identity는 semantic validation 전에 409 `SURVEY_NOT_OPEN`이다.
- accepted text는 그대로 저장하고 optional Answer는 생략한다. Question/Option canonical order, SCALE/NUMBER normalization, fixed compact UTF-8 JSON과 SHA-256 vector가 통과한다.

## 6.3 Same-Survey concurrency

두 Product path는 transaction 시작부에서 같은 Survey row의 `PESSIMISTIC_WRITE`를 얻는다.

```text
mutation-first
→ submit이 Survey lock 대기
→ committed latest Question structure 재검증
→ stale Response commit 0

submit-first
→ mutation이 Survey lock 대기
→ canonical Response commit
→ real survey_responses EXISTS
→ SURVEY_STRUCTURE_LOCKED, structure write 0
```

`should_waitAndValidateLatestStructure_when_mutationOwnsSurveyLockFirst`와 `should_commitResponseThenRejectStructureMutation_when_submitOwnsSurveyLockFirst`가 실제 PostgreSQL blocking order와 final row state를 검증한다. 따라서 첫 Response와 structure mutation이 모두 commit되는 forbidden outcome은 없다. Lock timeout은 safe 503과 partial write 0으로 끝나며 같은 `clientSubmissionId` retry가 성공한다.

## 6.4 Transport and respondent identity

- exact Public POST만 anonymous + CSRF exempt이며 broad `/api/public/**` exemption은 없다.
- raw 1 MiB boundary, +1 byte 413, non-JSON 415와 bounded ephemeral 429가 Product write 전에 적용된다.
- Production proxy-trust gate 전 forwarded identity header를 사용하지 않는다.
- Product DB에 IP, respondent token 또는 persistent submission tracking을 저장하지 않는다.
- `clientSubmissionId`는 form instance memory에만 있고 localStorage/sessionStorage/cookie/IndexedDB/URL에 쓰지 않는다.

## 6.5 Respondent frontend

- `/s/:slug`는 Admin guard 밖의 유일한 public route이며 dedicated strict DTO/client를 사용한다.
- Intro → ordered step/progress → submit → completion과 all six Question type이 통과한다.
- Question 0개 OPEN Survey도 같은 form UUID로 `answers=[]`를 제출해 201/200 completion으로 이동한다.
- pending 동안 duplicate submit, navigation과 Answer mutation을 차단하고 retryable failure 뒤 controls를 다시 활성화한다.
- 400/413/429/503/network retry는 UUID를 유지하며 cross-slug stale success/error/finally는 새 form state를 변경하지 않는다.
- raw backend `message`를 UI 분기나 사용자-facing text authority로 사용하지 않고 stable code/path를 한국어 UX로 변환한다.
- heading/fieldset/legend/label/describedby/focus/live status와 360px-targeted layout regression이 통과한다.

# 7. Negative Scope and Static Audit

```text
Phase 4 Result dashboard                  0
Creator Response list/detail              0
Public Response GET/edit/delete            0
aggregation/chart                          0
CSV export                                 0
V7 migration                               0
Production deploy                          0
Cloudflare/public activation               0
GHCR publish                               0
Secret mutation                            0
live migration / backup / restore          0
Release tag creation/push                  0
GitHub Release publication                 0
```

Completion evidence change 자체도 documentation only다.

```text
Backend source diff       0
Frontend source diff      0
Flyway/schema diff        0
Dependency/lockfile diff  0
CI workflow diff          0
Docker/Compose diff       0
```

Historical Phase 2 completion/release evidence와 accepted ADR은 당시 gate 상태를 기록하므로 이번 current-status 동기화에서 다시 쓰지 않았다.

# 8. Review Gate

Current integrated evidence review:

```text
P0            0
P1            0
P2            0
unresolved    0
```

Completion PR은 final exact head에서 Backend, Frontend와 Infrastructure를 다시 통과해야 하며 ordinary docs → dev PR의 `ARM64 Release Artifact`는 expected `SKIPPED`여야 한다. Final head SHA, Hosted run ID, mergeability와 review-thread 결과는 commit이 자신의 future SHA/run을 포함할 수 없으므로 PR body에 기록한다.

# 9. Conclusion and Follow-up Ownership

```text
Phase 3 Completion / Integration Evidence  PASS
Phase 3 Public Survey/Response             COMPLETE ON DEV — PENDING RELEASE GATE
```

사용자가 이 evidence PR을 merge하고 GPT가 latest merged `dev`를 검증한 뒤에만 Issue #55를 completed 처리한다. 다음 active slice는 Phase 3 Gate 3 Main Release Candidate Evidence 하나이며 full `main...dev` diff, native ARM64 target artifact와 disposable PostgreSQL Flyway compatibility/recovery-impact classification을 소유한다.

실제 `dev → main` Release PR, Release tag, GitHub Release, Phase 4와 Production은 각각 별도 Gate다.
