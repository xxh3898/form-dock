---
title: Phase 4 Main Release Evidence
status: active
version: 1.0
last_updated: 2026-08-25
---

# 1. Gate 3 판정 경계

이 문서는 Issue #76의 Phase 4 Gate 3 release-evidence candidate를 검증한다. Gate 3는 repository/main release eligibility만 소유하며 실제 `dev → main` Release, tag, GitHub Release 또는 Production readiness/activation을 소유하지 않는다.

첫 immutable evidence head/run과 local/disposable evidence 판정은 다음과 같다.

```text
Phase 4 Completion / Integration Evidence  PASS
Gate 3 full release diff                   PASS
Gate 3 Flyway compatibility                PASS
Gate 3 release regression                  PASS
Gate 3 native ARM64 artifact               PASS
Phase 4 main RC                            READY TO OPEN RELEASE — EVIDENCE PR MERGE REQUIRED
Production                                 NOT AUTHORIZED
Release tag                                NOT AUTHORIZED BEFORE VERIFIED MAIN
```

Gate 3 `PASS`는 이 evidence PR이 사용자에 의해 merge되고 latest `dev` exact SHA/CI가 다시 검증된 뒤 별도 Phase 4 `dev → main` Release Issue/PR을 열 수 있다는 뜻이다. Actual Release PR, merge, `v0.4.0`, GitHub Release와 Production 작업은 이 문서로 승인되지 않는다.

# 2. Exact Baseline and Ancestry

2026-08-25에 live remote와 Git history에서 확인한 pre-publication baseline은 다음과 같다.

```text
main SHA       2d988e3cc52710023eb4e8da10d39e7e42676a70
main tree      21a904517ffb62e5eecb82aebca587cb22066107
dev SHA        296ce3d6c594f15f3d65c3e18f8764c9d7b01335
dev tree       d4f9b073d112538106699f1f1dc16b405e6fedef
merge-base     2d988e3cc52710023eb4e8da10d39e7e42676a70
main ancestor  PASS
behind         0
ahead          8 commits
```

Source `main...dev`는 8 commits, 49 files, 6,900 additions, 89 deletions이며 status는 added 24 / modified 25 / deleted 0 / renamed 0이다. Evidence document와 Gate-3-owned status sync를 포함한 final candidate content는 10 commits, 50 files, 7,194 additions, 90 deletions이며 status는 added 25 / modified 25 / deleted 0 / renamed 0이다.

Commit이 자신의 SHA/tree와 future Hosted run을 포함할 수 없는 self-reference 때문에 final exact head SHA/tree와 final Hosted run은 PR body가 authority다. Repository 문서는 source baseline과 첫 immutable evidence head/run을 고정한다.

# 3. Full Release Diff Inventory

Final evidence candidate content의 `main...candidate` category inventory는 다음과 같다.

| Category | Files | Additions | Deletions | Release content |
|---|---:|---:|---:|---|
| Backend | 20 | 3,400 | 1 | Creator-owned Response list/detail, bounded summary, streaming CSV와 PostgreSQL integration regression |
| Frontend | 11 | 2,647 | 3 | Admin Results overview/detail/CSV, strict parser와 360px min-content containment |
| Docs | 17 | 1,139 | 81 | Phase 4 contract/status/completion/main Release Candidate evidence |
| Root | 2 | 8 | 5 | README/AGENTS current Phase status |

Backend 세부 file count는 production Java 15, test Java 4, boundary README 1이다. Frontend는 runtime source 8, test 2, boundary README 1이다. `infra/`, `.github/workflows/`, Flyway와 dependency/lockfile change는 0이다.

## 3.1 Expected Phase 4 content

- Creator-owned, newest-first paginated Response list와 current Question-order detail
- owner/non-deleted Survey first concealment와 internal idempotency metadata 비노출
- bounded grouped summary, Choice percentage와 Scale distribution
- UTF-8 BOM/RFC 4180/CRLF, deterministic columns/rows와 memory-bounded CSV export
- Admin Results overview/detail/CSV UI와 safe Korean error state
- backend-owned microsecond Response order를 frontend가 손실 재판정하지 않는 parser correction
- 360×800에서 page-level overflow를 제거하는 Results Grid min-content containment
- Phase 4 completion evidence와 Gate-3-owned status documentation

위 항목은 accepted Phase 4 scope이며 Response mutation, Public Results, advanced analytics 또는 Production drift가 아니다.

# 4. Phase 4 Completion Provenance

| Slice | Issue / PR | Final reviewed head | `dev` integration | Reviewed/integrated tree | Result |
|---|---|---|---|---|---|
| 4-A Creator Response Read | #64 / #65 | `e0fa3ceccac273be6290df1122158e9369ee74c6` | `444d0614df76bb78b96d58f833e741264be0783d` | `fbee42e5514185ea69be8b79d251c1f3bcc81dd5` | `PASS` |
| 4-B Result Summary | #66 / #67 | `7b344ab630b2949aba54df5382306a01615dacab` | `088bbb22360d5dced2ee77ecd8622abff02c859f` | `9d8c532ff7c3d8a0e105ed5f8e940500ebc230d4` | `PASS` |
| 4-C CSV Export | #68 / #69 | `26121c1663defd85193fcf0789d5c7b33684bec9` | `cb6e34f7bf3bd3e30a0b2465c4b987ede9469941` | `6dc8fd42b6891dd78d798bef8d451a3dc55c215e` | `PASS` |
| 4-D Results Frontend | #70 / #71 | `088e10b3322f0151c61cefb66f37fe6c387d28a3` | `2dc02550307c915a97a49f85dd4e8228e2f9161f` | `50a15b26e6fcd4b09fbacb46f46fe15161cf16c8` | `PASS` |
| Responsive blocker | #73 / #74 | `9fa7d8465e72c18cdea6470da5b4daa37afc70de` | `f32750df138df8a15d48b0eb178279672f29213e` | `1f8d09190ccf8e37fa7e9f45def89dafd8979803` | `PASS` |

PR #75 completion evidence final reviewed head `69047a060dfc1dc8440b10eae9efdd17de0b129b`와 merged `dev@296ce3d6c594f15f3d65c3e18f8764c9d7b01335`는 tree `d4f9b073d112538106699f1f1dc16b405e6fedef`로 동일하다.

Merged dev push run [32838895928](https://github.com/xxh3898/form-dock/actions/runs/32838895928)은 Backend 171/171, Frontend 11 files/104 tests와 Infrastructure `SUCCESS`; ordinary dev push의 ARM64는 policy상 expected `SKIPPED`다. Chrome 151의 360×800 actual-browser와 LibreOfficeDev 26.8 CSV compatibility evidence는 Product/runtime tree `d4f9b073...`를 소유하는 [Phase 4 Completion Evidence](phase-4-completion-evidence.md)에 기록돼 있다. 이 Gate 3 branch의 Product/runtime tree가 동일하므로 manual evidence를 의미 없이 재실행하지 않는다.

# 5. Flyway Inventory and Immutability

Current candidate의 versioned migration은 정확히 여섯 개다. SHA-256은 candidate file bytes에서 직접 계산했다.

| Version | File | SHA-256 | Responsibility |
|---|---|---|---|
| V1 | `V1__create_users.sql` | `11e46407f3dbf7c61653f848051053848b7776e9643b3910bc00f109c877b7e1` | Creator/User schema |
| V2 | `V2__create_spring_session.sql` | `83da1d682414421cacecc942191dd27dc405171b9ca92c03bba571a47937a7f4` | Spring Session JDBC infrastructure |
| V3 | `V3__create_surveys.sql` | `2db4db33f33bf7f22ab6cde4a2153cf6019d3472285f1a99eeb7d3a354ffd9d8` | Survey schema |
| V4 | `V4__create_questions_and_options.sql` | `5471283947e48712f8fe53c26d24a0f7d5d53bca8d22f0034ef95a872e3cdc00` | Question/Option schema |
| V5 | `V5__create_survey_responses.sql` | `07db184601785853503e48d09c0fbfe8fa8836968e9d02604e39fde4b9bfc846` | canonical Response identity/lock authority |
| V6 | `V6__create_answers_and_answer_options.sql` | `199e85e2c5111a65cb664607c433184a4eb6815650bf95e4b34ecd1b8dfda752` | Answer/AnswerOption schema |

Released `main@2d988e3c...`과 candidate의 V1~V6는 byte-for-byte 동일하며 checksum diff는 0이다. V7+와 새 table/index/materialized authority는 0이고 Phase 4는 existing `survey_responses`, `answers`, `answer_options`를 read-only로 사용한다.

# 6. Disposable PostgreSQL 18.6 Compatibility

모든 database evidence는 exact disposable container/network, tmpfs와 `postgres:18.6-alpine3.23`을 사용했다. Live/shared database, persisted development volume과 Production data는 사용하지 않았다.

## 6.1 Clean V1→V6

Exact candidate Product image를 empty database에 시작한 결과:

```text
Flyway history                  1,2,3,4,5,6
owned tables                    9
application health              UP
database health                 UP
missing relation/Flyway error   0
```

Owned table 9개는 `users`, Spring Session 2개, `surveys`, Question/Option 2개, `survey_responses`, Answer/AnswerOption 2개다.

## 6.2 Released-main V6→candidate same-schema

Released exact `main@2d988e3cc52710023eb4e8da10d39e7e42676a70` application이 empty database에 V1→V6를 적용한 뒤 non-secret representative fixture를 생성했다.

```text
Creator/User                          1
Survey                                1 OPEN
Question                              6 — all six types
QuestionOption                        4
SurveyResponse                        1
Answer                                6
AnswerOption                          3
```

동일 database에서 candidate application을 시작한 뒤:

```text
Flyway version/checksum change        0
pending migration                     0
application/database health           UP / UP
Creator authentication                PASS
Phase 3 Public GET/submit replay       PASS
Phase 4 list/detail/summary/CSV        PASS
fixture counts before/after            1,1,6,4,1,6,3 / same
missing relation/Flyway error          0
```

Candidate는 schema/data migration을 실행하지 않았다. Existing same V6 schema에 read-only Results capability만 추가했다.

## 6.3 Previous-main application rollback boundary

Candidate 검증 뒤 released main application을 같은 disposable V6 database에 다시 시작했다.

```text
classification                 TESTED
application/database health    UP / UP
Creator authentication         PASS
representative V1~V6 fixture   preserved
Phase 3 Public GET/replay       PASS
Flyway version/checksum change 0
missing relation/Flyway error  0
```

Previous-main application rollback boundary는 `TESTED`다. Actual Production rollback/deploy 또는 live data compatibility는 Gate 4가 별도 소유한다.

# 7. Local and Integrated Regression

| Area | Result | Evidence |
|---|---|---|
| Candidate API image | `PASS` | exact Product tree backend Dockerfile bootJar build와 disposable V1→V6 startup/health |
| Frontend | `PASS` | pinned Node 24.19.0 disposable container, npm ci/lint/typecheck, 11 files/104 tests, failed/skipped 0, production build와 audit finding 0 |
| Compose | `PASS` | `.env.example` config render와 existing API/Web Dockerfile image build |
| Backend full local check | `NOT RUN — HOST POLICY` | host Java runtime이 없고 development container에 Docker socket을 노출하지 않음 |
| Merged dev Hosted | `PASS` | run 32838895928, Backend 171/171, Frontend 104/104, Infrastructure success |

## 7.1 First immutable evidence head

First immutable evidence head `8f71e30d5e7c464fe3018dc69683fb32f9e414eb`, tree `684e4bc22c085085f9c3de0ff68c49d1fbf6d2c1`의 Hosted run [32841250167](https://github.com/xxh3898/form-dock/actions/runs/32841250167)은 다음과 같다.

```text
head                    8f71e30d5e7c464fe3018dc69683fb32f9e414eb
tree                    684e4bc22c085085f9c3de0ff68c49d1fbf6d2c1
run                     32841250167
Backend                 SUCCESS — 171/171, failed 0, skipped 0
Frontend                SUCCESS — 11 files/104 tests, failed 0, skipped 0
Infrastructure          SUCCESS
ARM64 Release Artifact  SUCCESS
```

Backend는 Temurin Java 25, Gradle 9.7.0과 실제 `postgres:18.6-alpine3.23` Testcontainer에서 clean V1→V6, Creator/Auth/Spring Session, Survey lifecycle/structure lock, Phase 3 Public Survey/Response와 Phase 4 list/detail/summary/CSV regression을 실행했다. `FormDockApplicationIntegrationTest.should_runPostgres18_6Alpine3_23Testcontainer_when_testcontainersAreEnabled()`를 포함해 skipped test는 0이다.

Evidence sync로 head가 바뀌므로 final exact-head run은 PR body가 authority이며 네 job 모두 다시 성공해야 한다.

# 8. Native ARM64 Release Artifact Contract

Work branch는 `release-evidence/76-phase-4-main-rc`이고 PR target은 `dev`다. Existing workflow는 이 topology에서 `ARM64 Release Artifact`를 실행한다.

First immutable head에서 확인한 evidence:

```text
runner / uname             ARM64 / aarch64
checkout head              8f71e30d5e7c464fe3018dc69683fb32f9e414eb
API image                  architecture=arm64 os=linux
Web image                  architecture=arm64 os=linux
emulation/QEMU             not used — native runner
image publish              none
repository Secret usage    none
Production operation       none
```

Workflow, Dockerfile와 base image tag는 이 Issue에서 변경하지 않는다.

# 9. Security and Phase Boundary Audit

Candidate는 Creator session/CSRF와 owner concealment authority를 유지한다.

- Result JSON/CSV는 same-origin Creator session을 요구한다.
- unsafe Admin request의 CSRF contract와 arbitrary credentialed CORS 부재를 유지한다.
- owner/non-deleted Survey를 먼저 resolve해 unowned/deleted Result를 conceal한다.
- Result DTO/CSV에 `clientSubmissionId`, `payloadHash`, owner/session metadata를 노출하지 않는다.
- anonymous boundary는 기존 Public Survey GET/Public Response POST 이상으로 확대하지 않는다.

Static negative-scope scan:

```text
Response edit/delete/exclude               0
Public Response GET/detail                 0
Public Results page                        0
respondent identity tracking               0
raw Text/Number summary arrays             0
NUMBER advanced statistics                 0
result search/filter/user sort/chart       0
V7/new schema/table/index                  0
Production Compose activation change       0
Production deploy                          0
Cloudflare/public activation               0
GHCR publish                               0
Secret mutation                            0
live migration / backup / restore          0
Release tag creation/push                  0
GitHub Release publication                 0
```

이 evidence PR 자체의 expected scope는 documentation only다. Product source/test, migration/schema, dependency/lockfile, Dockerfile/Compose와 workflow를 base `dev` 대비 변경하지 않는다.

# 10. Recovery Impact

```text
NO DATA/SCHEMA IMPACT
```

근거:

- released main과 candidate의 V1→V6 file bytes/checksum 동일;
- V7+, 새 table/index/schema authority와 migration-required data rewrite 0;
- Phase 4는 existing canonical Response/Answer 관계를 read-only로 사용;
- released-main V6 fixture의 candidate read와 row-count/checksum 보존 검증;
- previous-main application을 same database에 재기동해 schema downgrade 없이 core behavior 복구 검증.

이 분류는 repository release의 data/schema impact만 뜻한다. Production backup/restore, deploy, rollback과 target health acceptance를 완료했다는 뜻이 아니다.

# 11. Review Gate and Conclusion

Pre-publication local Gate 3 review:

```text
git diff --check                    PASS
conflict markers                   0
high-confidence Secret findings    0
Product/runtime/schema/CI drift    0
P0 / P1 / P2 / unresolved          0 / 0 / 0 / 0
```

```text
Phase 4 Results / Export               COMPLETE ON DEV — RELEASE CANDIDATE READY
Phase 4 Gate 3                         PASS — EVIDENCE PR MERGE REQUIRED
Actual dev → main Release              NOT YET AUTHORIZED UNTIL EVIDENCE MERGE
v0.4.0                                 NOT AUTHORIZED BEFORE VERIFIED MAIN
Production                             NOT AUTHORIZED
```

Evidence sync로 생성되는 final exact head에서 Backend, Frontend, Infrastructure와 ARM64 Release Artifact가 모두 다시 성공해야 PR을 READY로 전환한다. Evidence PR merge/latest `dev` 검증 전에는 actual Release Issue/PR, `v0.4.0`, GitHub Release 또는 Production 작업을 시작하지 않는다.
