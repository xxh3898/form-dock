---
title: Phase 3 Main Release Evidence
status: active
version: 1.0
last_updated: 2026-08-23
---

# 1. Gate 3 판정 경계

이 문서는 Issue #57의 Phase 3 Gate 3 release-evidence candidate를 검증한다. Gate 3는 repository/main release eligibility만 소유하며 Production readiness, actual `dev → main` Release 또는 tag를 소유하지 않는다.

첫 immutable evidence head와 local/disposable evidence를 검토한 판정은 다음과 같다.

```text
Phase 3 Completion / Integration Evidence  PASS
Gate 3 full release diff                   PASS
Gate 3 Flyway compatibility                PASS
Gate 3 release regression                  PASS
Gate 3 native ARM64 artifact               PASS
Gate 3 recovery classification             RECOVERY PLAN REQUIRED
Phase 3 main RC                            READY TO OPEN RELEASE — EVIDENCE PR MERGE REQUIRED
Phase 4 Results / Export                   NOT AUTHORIZED
Production                                 NOT AUTHORIZED
Release tag                                NOT AUTHORIZED BEFORE VERIFIED MAIN
```

Gate 3 `PASS`는 이 evidence PR이 user-merged되고 latest `dev`가 검증된 뒤 별도 `dev → main` Release Issue/PR을 열 수 있다는 뜻이다. Actual Release PR, merge, tag, Phase 4와 Production을 승인하지 않는다.

# 2. Exact Baseline and Ancestry

2026-08-23에 live remote와 Git history에서 확인한 pre-publication baseline은 다음과 같다.

```text
main SHA       5f7b00384c840203edbfb689f05ed0abe4737b43
main tree      f9439c9807505103e431e113bdc2c966bc701d3e
dev SHA        0fedc03b2f2240f34799440bb3d759c283d19e6e
dev tree       32e0d0b63a60fa830c8f1b3b7b60a2c343fab809
merge-base     5f7b00384c840203edbfb689f05ed0abe4737b43
main ancestor  PASS
behind         0
ahead          12 commits
```

Source `main...dev`는 12 commits다. 이 문서를 포함한 final evidence candidate content는 14 commits, 93 files, 9,446 additions, 690 deletions이며 status는 added 41 / modified 52 / deleted 0 / renamed 0이다.

Commit이 자신의 SHA/tree를 포함할 수 없는 self-reference 때문에 final exact head SHA/tree와 final Hosted run은 PR body가 authority다. Repository 문서는 첫 immutable evidence head/run과 source baseline을 고정한다.

# 3. Full Release Diff Inventory

Final evidence candidate content의 exact `main...candidate` category inventory는 다음과 같다.

| Category | Files | Additions | Deletions | Release content |
|---|---:|---:|---:|---|
| Backend | 41 | 4,650 | 17 | Public Survey GET, V6 Response persistence/canonicalization, atomic Public POST, security/transport/concurrency tests |
| Frontend | 24 | 3,418 | 333 | `/s/:slug` respondent flow, six type/zero-question/retry/pending/stale isolation와 한국어 safe-code UX |
| Docs | 22 | 1,183 | 157 | Phase 3 contract/status/completion/release evidence와 Release Tag Policy |
| Governance | 4 | 178 | 178 | Korean Issue/PR template wording과 lifecycle contract |
| Root | 2 | 17 | 5 | README/AGENTS current Phase와 language/governance status |

Backend 세부 file count는 production Java 27, test Java 10, resources 3, boundary README 1이다. Frontend는 source/test 22와 boundary/HTML 2다. `infra/` change는 0이며 GitHub workflow change는 0이다.

## 3.1 Expected Phase 3 content

- exact anonymous `GET /api/public/surveys/{slug}`와 unavailable-state 404 concealment
- V6 Answer/AnswerOption schema, one canonical Response authority와 deterministic SHA-256 payload
- exact anonymous/CSRF-exempt `POST /api/public/surveys/{slug}/responses`
- lifecycle/replay-before-OPEN ordering, atomic aggregate와 same-Survey pessimistic lock
- 1 MiB/415/429 transport boundary와 forwarded identity header 불신
- `/s/:slug` respondent frontend, memory-only `clientSubmissionId`, zero-question와 cross-slug isolation
- 한국어 사용자 UI/API-field presentation과 Release Tag Policy 문서
- Phase 3 completion evidence

위 항목은 accepted Phase 3 scope이며 Phase 4 또는 Production drift가 아니다.

# 4. Phase 3 Completion Provenance

| Slice | Issue / PR | Reviewed tree | `dev` integration | Integration tree | Match |
|---|---|---|---|---|---|
| 3-A Public Survey Read | #45 / #46 | `e402eb0320de2960e1b5ef85de77091c01711f2a` | `1105bdad999f522c92b68bc2861c285dbed96d89` | `e402eb0320de2960e1b5ef85de77091c01711f2a` | `PASS` |
| 3-B Response Data & Canonicalization | #47 / #48 | `85906c5a09c414fb2d0dda332cd53c9019533565` | `2b76f77cbef868b30554e85abc47f8b0aa549174` | `85906c5a09c414fb2d0dda332cd53c9019533565` | `PASS` |
| 3-C Atomic Public Submission | #49 / #50 | `626fa37bab622b26702784b9ca6a40278ff7cc7f` | `ba96b96e728d36def3e482e58de13c2df8025034` | `626fa37bab622b26702784b9ca6a40278ff7cc7f` | `PASS` |
| 3-D Respondent Frontend | #51 / #52 | `a83bf31b8d23aa4b01a6c094c90e33ee7a01b47a` | `7a53a72044e81453676ae08a783f070531091b4d` | `a83bf31b8d23aa4b01a6c094c90e33ee7a01b47a` | `PASS` |

PR #54의 Release Tag Policy는 Product/runtime/schema/CI를 바꾸지 않은 docs-only governance change다. PR #56 completion evidence reviewed head `c02d8e2705a5a0ef6b2d1921b7fc9b91a45b4f62`와 merged `dev@0fedc03b2f2240f34799440bb3d759c283d19e6e`는 tree `32e0d0b63a60fa830c8f1b3b7b60a2c343fab809`로 동일하다.

Merged dev push run [32563741549](https://github.com/xxh3898/form-dock/actions/runs/32563741549)은 Backend 151/151, Frontend 86/86와 Infrastructure `SUCCESS`; ordinary dev push의 ARM64는 expected `SKIPPED`다.

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

V1~V5는 released `main`과 byte-for-byte 동일하다. V6만 새 migration이며 `answers`, `answer_options` 두 table과 관련 PK/FK/UNIQUE/CHECK 14개를 추가한다. Existing V5 `survey_responses`는 변경하지 않으며 V7은 없다.

# 6. Disposable PostgreSQL 18.6 Compatibility

모든 database evidence는 isolated private network와 tmpfs를 사용한 disposable `postgres:18.6-alpine3.23`에서 실행했다. Live/shared database, persisted development volume과 Production data는 사용하지 않았다.

## 6.1 Clean V1→V6

Candidate application을 empty database에 시작해 Flyway와 health를 확인했다.

```text
Flyway history                  1,2,3,4,5,6
owned tables                    9
application health              UP
database health                 UP
missing relation/Flyway error   0
```

Owned table count 9는 `users`, Spring Session 2개, `surveys`, Question/Option 2개, `survey_responses`, Answer/AnswerOption 2개다.

## 6.2 Released V5→candidate V6 upgrade

Released exact main application이 empty database에 V1→V5를 적용한 뒤 representative non-secret fixture를 만들었다.

```text
before upgrade migration count/max       5 / 5
User                                      1
Spring Session / attribute                1 / 1
Survey / Question / Option                1 / 1 / 1
SurveyResponse                            1
```

Candidate application을 같은 database에 시작해 V6를 적용한 뒤 결과:

```text
after upgrade migration count/max         6 / 6
V1~V5 representative fixture              1 / 1 / 1 / 1 / 1 / 1 / 1 preserved
V6 tables                                 answers, answer_options
V6 table constraints                      14
Answer / AnswerOption representative row  1 / 1
application health / database health      UP / UP
missing relation/Flyway error             0
```

V6는 existing V1~V5 schema/data를 drop, rename, rewrite 또는 update하지 않는다.

## 6.3 Previous-main application / forward-V6 schema

Released `main@5f7b00384c840203edbfb689f05ed0abe4737b43` application image를 V6가 적용된 동일 disposable database에 다시 시작했다.

```text
classification                  TESTED
database current version        6
application startup             PASS
Actuator health / DB            UP / UP
V1~V5 fixture                   preserved
V6 Answer/AnswerOption fixture  preserved
missing relation/Flyway error   0
```

Flyway의 additive forward-schema tolerance와 previous application startup/health는 `TESTED`다. Actual Production deployment rollback과 live data compatibility는 Gate 4가 별도 검증한다.

# 7. Local and Integrated Regression

| Area | Result | Evidence |
|---|---|---|
| Candidate API image | `PASS` | exact `dev@0fedc03b...` backend Dockerfile bootJar build와 disposable V1→V6 startup/health |
| Frontend | `PASS` | pinned Node 24.19.0 container, npm ci, lint, typecheck, 9 files / 86 tests, failed/skipped 0, production build, audit finding 0 |
| Compose | `PASS` | `.env.example` config render와 existing API/Web Dockerfile image build |
| Backend full local check | `NOT RUN — HOST POLICY` | host Java가 없고 development container Docker socket mount를 사용하지 않음 |
| Merged dev Hosted | `PASS` | run 32563741549, Backend 151/151, Frontend 86/86, Infrastructure success |

## 7.1 First immutable evidence head

첫 immutable evidence head `615e6d4982ea6cf0ab3cca6f60e168beba32d105`, tree `fce84ed43e60f8c91228c6223b5b10c2843f018a`의 Hosted run [32585549853](https://github.com/xxh3898/form-dock/actions/runs/32585549853)은 다음과 같다.

```text
Backend                 SUCCESS — 151/151, failed 0, skipped 0
Frontend                SUCCESS — 9 files / 86 tests, failed 0, skipped 0
Infrastructure          SUCCESS
ARM64 Release Artifact  SUCCESS
```

Backend는 Temurin Java 25와 PostgreSQL `18.6-alpine3.23` Testcontainers에서 clean V1→V6, Creator/Auth, Public Survey GET, Response persistence/canonicalization, atomic lifecycle/idempotency, same-Survey concurrency와 transport/security regression을 실제 실행했다. Frontend는 Node 24.19.0, npm ci/lint/typecheck/test/build와 audit finding 0을 확인했다.

ARM64 job은 exact head를 checkout한 native `ubuntu-24.04-arm` runner에서 existing Dockerfile을 변경 없이 검증했다.

```text
runner / uname             ARM64 / aarch64
checkout head              615e6d4982ea6cf0ab3cca6f60e168beba32d105
API image                  architecture=arm64 os=linux
Web image                  architecture=arm64 os=linux
emulation/QEMU             not used — native runner
image publish              none
repository Secret usage    none
Production operation       none
```

Evidence sync로 head가 바뀌므로 final exact-head run은 PR body가 authority이며 네 job 모두 다시 성공해야 한다.

# 8. Security and Phase Boundary Audit

Candidate는 Creator session/CSRF authority를 보존하면서 exact Public boundary만 추가한다.

- Creator Admin session authority와 unsafe Admin CSRF 유지
- exact `GET /api/public/surveys/{slug}`와 exact `POST /api/public/surveys/{slug}/responses`만 anonymous
- exact Public POST만 CSRF exempt; broad `/api/public/**` unsafe exemption 없음
- credentialed arbitrary CORS 없음
- forwarded identity header를 respondent identity authority로 사용하지 않음
- Product DB의 IP/respondent token tracking과 frontend persistent submission storage 없음

Static negative-scope scan:

```text
Phase 4 Result dashboard                  0
Creator Response list/detail/read UI/API  0
Public Response GET/edit/delete            0
aggregation/chart                          0
CSV export                                 0
V7+ migration                              0
Production Compose activation change       0
Production deploy                          0
Cloudflare/public activation               0
GHCR publish                               0
Secret mutation                            0
live migration / backup / restore          0
Release tag creation/push                  0
GitHub Release publication                 0
```

이 evidence PR 자체의 expected scope는 documentation only다. Product source/test, migration/schema, dependency/lockfile, Dockerfile/Compose와 workflow를 `dev` 대비 변경하지 않는다.

# 9. Recovery Impact

```text
RECOVERY PLAN REQUIRED
```

Released main은 V1→V5이고 candidate가 additive V6 Answer/AnswerOption schema를 추가한다. V6가 existing data를 변경하지 않고 disposable V5→V6와 previous-main/forward-V6가 통과했어도 reverse/down migration은 없다.

Gate 4 / Production activation 전에 actual target에서 완료할 blocking actions:

1. existing live data가 있으면 predeploy PostgreSQL logical backup 생성과 정상성 검증;
2. recovery requirement에 맞는 isolated scratch restore 검증;
3. target retention과 off-host copy policy 확정;
4. disposable V5→V6 evidence와 target-specific 차이 확인;
5. previous application / forward V6 rollback boundary와 forward-fix 절차 확정;
6. Gate 3-approved artifact의 target deployment와 API/Web/PostgreSQL health acceptance;
7. Production authorization 뒤에만 public respondent smoke 실행.

이 Gate는 live backup, restore, migration, deployment 또는 public smoke를 실행하지 않았으며 operational recovery readiness를 완료로 표시하지 않는다.

# 10. Review Gate and Conclusion

First immutable evidence head와 local Gate 3 review:

```text
git diff --check                    PASS
conflict markers                   0
high-confidence Secret findings    0
Product/runtime/schema/CI drift    0
P0 / P1 / P2 / unresolved          0 / 0 / 0 / 0
```

```text
Phase 3 Gate 3 main Release Candidate  PASS
Phase 3 main RC                        READY TO OPEN — EVIDENCE PR MERGE REQUIRED
```

Final exact evidence-sync head의 Backend, Frontend, Infrastructure와 ARM64 Release Artifact, final full diff/static/docs/security scan과 review thread 결과는 PR body가 기록한다. 이 gate가 모두 통과해야 PR을 READY로 전환하며 Evidence PR merge/latest dev 검증 전에는 actual Release Issue/PR을 만들지 않는다.
