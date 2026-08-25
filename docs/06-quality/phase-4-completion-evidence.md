---
title: Phase 4 Results / Export 완료 근거
status: active
version: 1.0
last_updated: 2026-08-25
---

# 1. 판정과 경계

최신 merged `dev`의 Phase 4-A→D Product tree, responsive blocker 보정과 integration validation을 검토한 결과는 다음과 같다.

```text
Phase 0                          COMPLETE
Application Scaffold            COMPLETE
Phase 1 Creator Foundation      COMPLETE + RELEASED
Phase 2 Survey Builder           COMPLETE + RELEASED
Phase 3 Public Survey/Response   COMPLETE + RELEASED
Phase 4 Results / Export         COMPLETE ON DEV — PENDING RELEASE GATE
Gate 3 Release Candidate         PENDING COMPLETION EVIDENCE MERGE
Production                       NOT AUTHORIZED

Phase 4 integration evidence     PASS
P0 / P1 / P2 / unresolved        0 / 0 / 0 / 0
```

`COMPLETE ON DEV`는 Creator-owned Response list/detail, bounded summary, memory-bounded CSV export와 Admin Results UI가 `dev`에 통합됐다는 뜻이다. `dev → main`, native ARM64 Release Artifact, `v0.4.0`, GitHub Release 또는 Production activation을 뜻하지 않는다. 이 상태의 Source of Truth 효력과 후속 Gate 3 착수 권한은 이 completion evidence PR이 사용자에 의해 `dev`에 merge되고 exact merged `dev` validation이 확인된 뒤 발생한다.

# 2. Exact Baseline

2026-08-25에 live remote와 Git history에서 확인한 authoritative baseline은 다음과 같다.

```text
main SHA       2d988e3cc52710023eb4e8da10d39e7e42676a70
main tree      21a904517ffb62e5eecb82aebca587cb22066107
dev SHA        f32750df138df8a15d48b0eb178279672f29213e
dev tree       1f8d09190ccf8e37fa7e9f45def89dafd8979803
merge-base     2d988e3cc52710023eb4e8da10d39e7e42676a70
main ancestor  PASS
```

Issue #72의 원래 baseline `dev@2dc02550307c915a97a49f85dd4e8228e2f9161f` actual-browser smoke에서 360px page-level horizontal overflow가 발견됐다. 별도 Issue #73 / PR #74가 그 Product defect만 수정했고, final reviewed head `9fa7d8465e72c18cdea6470da5b4daa37afc70de`, synthetic merge `82a5cf26166970ef6937a560f06e79df136ed8b0`과 merged `dev@f32750df...`가 모두 tree `1f8d09190ccf8e37fa7e9f45def89dafd8979803`을 가진다. 따라서 new `dev`는 unrelated drift가 아니라 Issue #72가 승인한 blocker-resolution baseline이다.

# 3. Phase 4 Integration Provenance

| Slice | Issue / PR | Final reviewed head | PR synthetic merge | Reviewed / synthetic / integrated tree | `dev` integration | Result |
|---|---|---|---|---|---|---|
| 4-A Creator Response Read | [#64](https://github.com/xxh3898/form-dock/issues/64) / [#65](https://github.com/xxh3898/form-dock/pull/65) | `e0fa3ceccac273be6290df1122158e9369ee74c6` | `998f9e6a10442fc29fe7cf44ca7f2f1fbfef12d4` | `fbee42e5514185ea69be8b79d251c1f3bcc81dd5` | `444d0614df76bb78b96d58f833e741264be0783d` | `PASS` |
| 4-B Result Summary | [#66](https://github.com/xxh3898/form-dock/issues/66) / [#67](https://github.com/xxh3898/form-dock/pull/67) | `7b344ab630b2949aba54df5382306a01615dacab` | `24c87a1e6aaf91573a34d2df287527aa4777ea6c` | `9d8c532ff7c3d8a0e105ed5f8e940500ebc230d4` | `088bbb22360d5dced2ee77ecd8622abff02c859f` | `PASS` |
| 4-C CSV Export | [#68](https://github.com/xxh3898/form-dock/issues/68) / [#69](https://github.com/xxh3898/form-dock/pull/69) | `26121c1663defd85193fcf0789d5c7b33684bec9` | `cedfdc78c2b57ab70594f5e94019072f0ab61562` | `6dc8fd42b6891dd78d798bef8d451a3dc55c215e` | `cb6e34f7bf3bd3e30a0b2465c4b987ede9469941` | `PASS` |
| 4-D Results Frontend | [#70](https://github.com/xxh3898/form-dock/issues/70) / [#71](https://github.com/xxh3898/form-dock/pull/71) | `088e10b3322f0151c61cefb66f37fe6c387d28a3` | `34102a286a96e78260a74e62de77a21e32d9f350` | `50a15b26e6fcd4b09fbacb46f46fe15161cf16c8` | `2dc02550307c915a97a49f85dd4e8228e2f9161f` | `PASS` |

각 final PR Hosted run의 checkout log가 synthetic merge OID를 기록하고, 당시 확인한 synthetic tree와 final reviewed head tree가 일치한다. GitHub이 보존 기간이 지난 synthetic commit object를 더 이상 Git API로 제공하지 않더라도 CI checkout log와 recorded tree evidence는 남는다. 네 squash integration commit은 reviewed head 자체를 ancestry로 보존하지 않지만, 각각의 integration tree가 reviewed/synthetic tree와 byte-for-byte 동일하고 current `dev` ancestry에 있다.

## 3.1 Responsive blocker fix provenance

| Issue / PR | Final reviewed head | PR synthetic merge | Reviewed / synthetic / integrated tree | `dev` integration | Result |
|---|---|---|---|---|---|
| [#73](https://github.com/xxh3898/form-dock/issues/73) / [#74](https://github.com/xxh3898/form-dock/pull/74) | `9fa7d8465e72c18cdea6470da5b4daa37afc70de` | `82a5cf26166970ef6937a560f06e79df136ed8b0` | `1f8d09190ccf8e37fa7e9f45def89dafd8979803` | `f32750df138df8a15d48b0eb178279672f29213e` | `PASS` |

PR #74는 `frontend/src/App.css`의 Results Grid min-content 전파 차단과 component-owned table scroll 경계만 변경했다. Phase 4 backend/API/data/schema/dependency/CI에는 변경이 없다.

# 4. Immutable Migration and Data Evidence

현재 versioned migration chain은 정확히 여섯 개다. SHA-256은 current file bytes에서 직접 계산했다.

| Version | File | SHA-256 | Responsibility |
|---|---|---|---|
| V1 | `V1__create_users.sql` | `11e46407f3dbf7c61653f848051053848b7776e9643b3910bc00f109c877b7e1` | Creator/User schema |
| V2 | `V2__create_spring_session.sql` | `83da1d682414421cacecc942191dd27dc405171b9ca92c03bba571a47937a7f4` | Spring Session JDBC infrastructure |
| V3 | `V3__create_surveys.sql` | `2db4db33f33bf7f22ab6cde4a2153cf6019d3472285f1a99eeb7d3a354ffd9d8` | owner-scoped Survey schema |
| V4 | `V4__create_questions_and_options.sql` | `5471283947e48712f8fe53c26d24a0f7d5d53bca8d22f0034ef95a872e3cdc00` | Question/Option schema |
| V5 | `V5__create_survey_responses.sql` | `07db184601785853503e48d09c0fbfe8fa8836968e9d02604e39fde4b9bfc846` | canonical Response identity/lock authority |
| V6 | `V6__create_answers_and_answer_options.sql` | `199e85e2c5111a65cb664607c433184a4eb6815650bf95e4b34ecd1b8dfda752` | Answer/AnswerOption schema |

`main@2d988e3cc52710023eb4e8da10d39e7e42676a70`과 current `dev`의 V1~V6 migration byte diff는 0이고 V7+는 없다. Phase 4는 새 table/index/materialized analytics authority를 만들지 않았고 `survey_responses`, `answers`, `answer_options`만 canonical Result read authority로 사용한다. Phase 4-D integration 뒤 backend source diff도 0이다.

# 5. Exact Integrated Validation

Latest merged dev run [32813625427](https://github.com/xxh3898/form-dock/actions/runs/32813625427)은 exact `dev@f32750df138df8a15d48b0eb178279672f29213e`의 `push` event다.

| Job | Result | Evidence |
|---|---|---|
| Backend | `SUCCESS` | Temurin Java 25, Gradle 9.7.0, `./gradlew --no-daemon clean check`, 171 total / 171 passed / 0 failed / 0 skipped |
| Frontend | `SUCCESS` | Node 24.19.0, npm 11.17.0, lint/typecheck, 11 files / 104 tests passed / 0 failed / 0 skipped, Vite 8.2.1 production build |
| Infrastructure | `SUCCESS` | Compose config와 API/Web Dockerfile image build |
| ARM64 Release Artifact | `SKIPPED` | ordinary `dev` push의 expected policy; Gate 3 native ARM64 evidence로 재사용하지 않음 |

Backend log에서 `FormDockApplicationIntegrationTest.should_runPostgres18_6Alpine3_23Testcontainer_when_testcontainersAreEnabled()`가 실제 `postgres:18.6-alpine3.23` Testcontainer와 PostgreSQL major 18을 검증한다. `DatabaseMigrationIntegrationTest`와 application integration test는 clean Flyway V1→V6, expected table/index/constraint와 startup을 검증하며 skipped test는 0이다.

Frontend는 tracked source를 복사한 disposable `node:24.19.0-alpine3.24` container에서 `npm ci`, lint, typecheck, 11 files / 104 tests, production build와 npm audit finding 0을 다시 통과했다. 이 completion PR final exact head에서도 Hosted required jobs를 다시 실행해야 한다.

# 6. Integrated Acceptance Matrix

| Area | Result | Source-grounded evidence |
|---|---|---|
| A. Creator/Auth regression | `PASS` | `CreatorAuthenticationIntegrationTest` login/logout/me, JDBC session restart/expiry, Admin 401, unsafe CSRF와 arbitrary credentialed CORS absence; isolated browser login |
| B. Response list/detail ownership + concealment | `PASS` | `CreatorResponseReadApiIntegrationTest` empty/default page, newest-first/tie-break, owner-first concealment, lifecycle, six-type/optional unanswered/metadata absence tests; isolated 3-Response list/detail |
| C. Summary count/percentage/scale semantics | `PASS` | `CreatorResponseSummaryApiIntegrationTest` zero/grouped/lifecycle/owner-first tests; isolated single 2/1/0, multiple 2/2/2와 scale 3.00/full buckets evidence |
| D. CSV encoding/security/determinism | `PASS` | `CreatorResponseCsvExportApiIntegrationTest` header-only/six-type/257-row/concealment tests, `Rfc4180CsvWriter`; isolated byte parser와 LibreOffice Calc import |
| E. Memory/query boundedness | `PASS` | fixed Survey-scoped grouped SQL, CSV forward-only cursor/fetch size 256/current-row state, 257-Response regression; whole export String/byte array/Response graph materialization 0 |
| F. Results frontend overview/list/detail/CSV flow | `PASS` | `ResultsWorkflow.test.tsx` 9 tests와 `resultsClient.test.ts` 9 tests, full 104-test suite, isolated actual Chrome overview/detail navigation |
| G. Error/empty/accessibility behavior | `PASS` | zero/out-of-range/transient/concealment safe-state tests, invalid route preflight, semantic headings/tables/time/live state, 360×800 page overflow 0와 keyboard focus evidence |
| H. Phase boundary / privacy negative scan | `PASS` | Public Response GET/detail, Public Results, mutation controls, internal metadata exposure, V7/schema/dependency/Production mutation 0 |

## 6.1 Response read/detail

- owner-scoped non-deleted Survey를 먼저 resolve하고 unknown/unowned/deleted Survey는 `SURVEY_NOT_FOUND`로 conceal한다.
- DRAFT/OPEN/CLOSED Result read, zero-response page와 out-of-range empty page가 정상이다.
- backend가 `submitted_at DESC, id DESC` ordering authority를 소유하고 frontend는 microsecond Instant를 millisecond로 축약해 재판정하지 않는다.
- detail은 current Question position과 selected Option position order를 유지하며 six type, optional unanswered `answer=null`, exact text와 canonical number를 반환한다.
- `clientSubmissionId`, `payloadHash`, owner/session metadata는 Result DTO에 없다.

## 6.2 Summary

- totalResponses, lastSubmittedAt와 current questionCount를 반환하고 Response 0건은 정상 zero summary다.
- SINGLE/MULTIPLE은 현재 configured Option을 zero-count까지 포함하며 percentage denominator는 answeredCount다.
- MULTIPLE 합계가 100%를 초과할 수 있다. Representative evidence는 세 Option이 각각 `2 / 3 = 66.67%`다.
- SCALE average는 2자리 HALF_UP이며 configured full bucket을 zero-count까지 포함한다. Representative evidence는 average `3.00`, value 1~5 count `0/1/1/1/0`이다.
- SHORT/LONG/NUMBER summary는 answeredCount만 반환하고 unbounded raw array 또는 NUMBER advanced statistics를 만들지 않는다.

## 6.3 CSV

- owner/schema 확인 뒤 body를 시작하고 Response를 `submitted_at ASC, id ASC`로 stream한다.
- UTF-8 BOM은 정확히 한 번, record terminator는 CRLF, comma/quote/embedded newline은 RFC 4180로 round-trip된다.
- Question/Option position으로 deterministic columns를 만들고 MULTIPLE_CHOICE는 Option별 boolean column이다.
- dynamic formula-like text는 apostrophe로 neutralize하지만 canonical negative NUMBER는 변경하지 않는다.
- positive fetch size 256과 current Response 한 행만 유지하며 Product write는 없다.

## 6.4 Results frontend

- `/admin/surveys/:surveyId/responses`와 `/admin/surveys/:surveyId/responses/:responseId`는 shared Admin guard 안에 있다.
- strict list/summary/detail parser, invalid route ID 선행 차단, stable error code 기반 한국어 safe state와 raw backend `message` 비사용이 통과한다.
- overview/Question summary/newest-first pagination/detail/CSV Blob download를 제공하고 Response mutation control과 Public Results state는 없다.
- CSV JSON error body는 다운로드하지 않고 pending single-flight와 object URL revoke를 보장한다.
- same-millisecond/different-microsecond Response를 정상 허용하며 server-owned order를 그대로 보존한다.

# 7. Isolated Application Smoke

Production credential/data/network를 사용하지 않고 loopback-only Compose project `dev-form-dock-issue72-final`에서 exact `dev@f32750df...` image를 build하고 기동했다. PostgreSQL은 `18.6-alpine3.23`, Flyway history는 V1→V6 전부 success였고 API `/actuator/health`의 application/database와 Web `/health`가 모두 healthy였다.

```text
local Creator bootstrap/login
→ Admin API Survey create
→ six Question type 구성
→ OPEN
→ anonymous Public GET
→ anonymous Response 3건 submit
→ Creator Results overview/list/detail
→ CSV export
→ CLOSED
```

Representative fixture는 Question 6개와 Response 3개를 사용했다.

| Check | Evidence | Result |
|---|---|---|
| Overview | total 3, questionCount 6, lastSubmittedAt 존재 | `PASS` |
| Newest-first list | response IDs `3, 2, 1` | `PASS` |
| SINGLE_CHOICE | configured Option counts `2/1/0` | `PASS` |
| MULTIPLE_CHOICE | counts `2/2/2`, 합계 percentage 200.01% 표시 가능 | `PASS` |
| SCALE | average `3.00`, buckets `0/1/1/1/0` | `PASS` |
| Text/Number | SHORT 3, LONG 2, NUMBER 2 answered | `PASS` |
| Detail | optional unanswered, `=1+1`, embedded newline/comma/quote, `-12.34`, selected Options | `PASS` |
| CSV | rows response IDs `1, 2, 3`, Option boolean columns, exact text/number semantics | `PASS` |
| CLOSED Result read | summary `200`, status CLOSED, total 3 | `PASS` |
| CLOSED Public read | `404 SURVEY_NOT_FOUND` | `PASS` |

검증 뒤 development containers/network는 일반 `down`으로 종료했고 `dev-form-dock-issue72-final_postgres-data` volume은 삭제하지 않았다. Production service/data에는 접근하거나 변경하지 않았다.

# 8. Actual Browser and Office Compatibility

## 8.1 Chrome 360×800

Chrome `151.0.0.0`에서 viewport `360×800`으로 exact-dev Web을 검증했다.

| Surface | Width evidence | Interaction evidence | Result |
|---|---|---|---|
| Results overview | document `360/360`, body `360/360`, main `328/328` client/scroll width | summary와 newest-first list 표시, CSV/페이지 action 표시 | `PASS` |
| Wide result tables | component regions `276/448`, `276/448`, `276/448`, `276/576` | horizontal overflow가 component-owned region에만 있고 `응답 보기` link로 상세 이동 | `PASS` |
| Individual detail | document/body `360/360`, page-level scrollable region 0 | exact multiline/formula-like text, choice/multiple/scale/number 표시 | `PASS` |
| Keyboard/focus | route heading focus 뒤 `Tab`으로 `응답 결과`, `설문 목록` 순서 | focused controls가 viewport `16..344` 안에 유지 | `PASS` |

Console warning/error는 0이었다. 임시 viewport override는 검증 뒤 reset했고 검증 tab은 닫았다.

## 8.2 LibreOffice Calc

Exact-dev CSV `phase-4-final-exact-dev.csv`를 LibreOfficeDev `26.8.0.0.alpha0` Calc engine으로 실제 import해 XLSX로 변환했다.

```text
rows                 4
cells                38
formula cells        0
UTF-8 Korean         PASS
comma / quote        PASS
embedded newline     PASS
formula-like text    '=1+1 문자 그대로 유지
negative NUMBER      -12.34 숫자 셀 유지
MULTIPLE booleans    PASS
```

Numbers 또는 parser-only 결과를 office compatibility 증거로 대체하지 않았다.

# 9. Negative Scope and Static Audit

```text
Public Response GET/detail                 0
Public Results page                        0
Response edit/delete/exclude               0
respondent identity tracking               0
clientSubmissionId/payloadHash exposure    0
owner/session metadata exposure            0
raw Text/Number summary arrays              0
NUMBER advanced statistics                 0
result search/filter/user sort              0
chart/global state dependency               0
V7/new schema/table/index                   0
Production deploy/activation                0
Cloudflare/GHCR/Secret mutation             0
live migration / backup / restore           0
release tag / GitHub Release                0
```

Completion evidence change 자체도 documentation only다.

```text
Backend source diff       0
Frontend source diff      0
Backend/Frontend tests    0
Flyway/schema diff        0
Dependency/lockfile diff  0
CI workflow diff          0
Docker/Compose diff       0
```

Historical Phase 1~3 completion/release evidence와 accepted ADR은 당시 gate 상태를 기록하므로 이번 current-status 동기화에서 다시 쓰지 않았다.

# 10. Review Gate

Current integrated evidence review:

```text
P0            0
P1            0
P2            0
unresolved    0
```

Completion PR은 final exact head에서 Backend, Frontend와 Infrastructure를 다시 통과해야 하며 ordinary docs → dev PR의 `ARM64 Release Artifact`는 expected `SKIPPED`여야 한다. Final head SHA, Hosted run ID, mergeability와 review-thread 결과는 commit이 자신의 future SHA/run을 포함할 수 없으므로 PR body에 기록한다.

# 11. Conclusion and Follow-up Ownership

```text
Phase 4 Completion / Integration Evidence  PASS
Phase 4 Results / Export                   COMPLETE ON DEV — PENDING RELEASE GATE
Gate 3 Release Candidate                   PENDING COMPLETION EVIDENCE MERGE
Production                                 NOT AUTHORIZED
```

사용자가 이 evidence PR을 merge하고 GPT가 latest merged `dev` exact SHA/CI를 검증한 뒤에만 Issue #72를 completed 처리한다. 다음 active slice는 Phase 4 Gate 3 Main Release Candidate Evidence 하나이며 full `main...dev` diff, native ARM64 target artifact, disposable PostgreSQL Flyway compatibility와 recovery-impact classification을 소유한다.

실제 `dev → main` Release PR, `v0.4.0`, GitHub Release, Phase 5와 Production은 각각 별도 Gate다.
