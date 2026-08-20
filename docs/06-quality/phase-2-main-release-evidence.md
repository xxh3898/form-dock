---
title: Phase 2 Main Release Candidate Evidence
status: active
version: 1.0
last_updated: 2026-08-20
---

# 1. Gate 3 판정 경계

이 문서는 Issue #34의 Phase 2 Gate 3 release-evidence candidate를 검증한다. Gate 3는 repository/main release eligibility만 소유하며 Production readiness 또는 activation을 소유하지 않는다.

첫 immutable evidence head와 local/Hosted evidence의 판정은 다음과 같다.

```text
Phase 2 completion               PASS
Gate 3 full release diff         PASS
Gate 3 native ARM64 artifact     PASS
Gate 3 Flyway compatibility      PASS
Gate 3 recovery classification   RECOVERY PLAN REQUIRED
Phase 2 main RC                  READY TO OPEN — EVIDENCE PR MERGE REQUIRED
Phase 3 Public Survey/Response   NOT AUTHORIZED
Production                       NOT AUTHORIZED
```

Gate 3 `PASS`는 이 evidence PR이 user-merged되고 latest dev가 검증된 뒤 별도 `dev → main` Release Issue/PR을 열 수 있다는 뜻이다. 실제 Release PR, merge, Production deploy, live migration 또는 다음 Product Phase를 자동 승인하지 않는다.

# 2. Exact Repository Baseline

2026-08-20 live remote에서 확인한 evidence 시작점은 다음과 같다.

```text
main SHA              751a9ee33282e20d46f9356ffecfbc110a692c9c
main tree             0345f65c529064d19296e625cfb2abfdbed90635
dev baseline SHA      921bec0c7f98316ac68db23847dee9ef67aea46d
dev baseline tree     642a537ba47a23349cfe9b6b299457f49f9e6101
merge base            751a9ee33282e20d46f9356ffecfbc110a692c9c
main ancestor         PASS
dev behind / ahead    0 / 10
first evidence head   061309782898eaba4658d99f048849eae667c321
first evidence tree   4af6cbf3c8d64c38d6c84566acf7b6ddd0d7fc40
```

Work branch `release-evidence/phase-2-main-rc`는 exact latest dev에서 생성했다. 첫 immutable evidence head는 Draft PR #35의 run [32381793799](https://github.com/xxh3898/form-dock/actions/runs/32381793799)에서 네 Hosted job을 통과했다. 최종 evidence-sync commit은 자신의 SHA/tree와 미래 run ID를 같은 commit에 self-reference할 수 없으므로 final exact head/tree/run은 PR body가 authoritative하게 기록하며 그 head도 네 Hosted job을 다시 통과해야 한다.

# 3. Full Release Diff Inventory

Final evidence-sync working tree에서 재계산한 `main...candidate` inventory는 다음과 같다. Commit count는 이 pending sync commit을 포함한다.

```text
commits       12
changed files 89
insertions    12,021
deletions     361
added         56
modified      33
deleted       0
renamed       0
```

| Area | Files | Release content |
|---|---:|---|
| Backend | 48 | Survey/Question domain, persistence, Admin API, structure lock, lifecycle/duplicate와 regression |
| Frontend | 17 | authenticated Survey list/create/builder/preview, typed client와 49-test regression |
| Documentation | 22 | Phase 2 entry, ADR-0006, API/data/quality/operations, completion과 main RC evidence |
| Repository root | 2 | current gate/status synchronization |

Expected Phase 2 release content:

- owner-scoped Survey CRUD, soft delete와 DRAFT/OPEN/CLOSED lifecycle;
- Question/Option six-type structure mutation, ordering와 validation;
- real V5 canonical Response COUNT/EXISTS structure-lock authority without Product Response writer;
- deep duplicate, owner concealment, bounded PostgreSQL row-lock error handling;
- authenticated Creator Survey Builder and read-only Admin Preview;
- V3 surveys, V4 questions/options와 V5 schema-only survey_responses migrations.

Expected Phase 2 runtime을 current main에 없다는 이유만으로 unexpected change로 분류하지 않는다. Final exact commit SHA/tree와 Hosted run은 PR body에서 이 inventory와 함께 다시 검증한다.

# 4. Phase 2 Provenance

[Phase 2 Completion Evidence](phase-2-completion-evidence.md)는 Phase 2-A/B/C/D reviewed tree, merge SHA, post-merge dev runs와 integrated acceptance matrix를 소유한다.

Latest completion evidence PR #33은 reviewed head `8f4bb11a69e8028c9dfb5ac71db8e0fa44afba7f`와 동일 tree로 `dev@921bec0c7f98316ac68db23847dee9ef67aea46d`에 merge됐다. Exact dev push run [32377300451](https://github.com/xxh3898/form-dock/actions/runs/32377300451)은 Backend 107/107, Frontend 49/49, Infrastructure `SUCCESS`, ordinary dev push의 ARM64 expected `SKIPPED`다.

# 5. Migration and PostgreSQL Compatibility

## 5.1 Immutable chain

Canonical versioned history는 정확히 다섯 개다.

| Version | File | SHA-256 |
|---|---|---|
| V1 | `V1__create_users.sql` | `11e46407f3dbf7c61653f848051053848b7776e9643b3910bc00f109c877b7e1` |
| V2 | `V2__create_spring_session.sql` | `83da1d682414421cacecc942191dd27dc405171b9ca92c03bba571a47937a7f4` |
| V3 | `V3__create_surveys.sql` | `2db4db33f33bf7f22ab6cde4a2153cf6019d3472285f1a99eeb7d3a354ffd9d8` |
| V4 | `V4__create_questions_and_options.sql` | `5471283947e48712f8fe53c26d24a0f7d5d53bca8d22f0034ef95a872e3cdc00` |
| V5 | `V5__create_survey_responses.sql` | `07db184601785853503e48d09c0fbfe8fa8836968e9d02604e39fde4b9bfc846` |

V1/V2는 released main, V3는 Phase 2-A introduction, V4/V5는 Phase 2-B introduction bytes와 동일하다. Versioned file count는 5이고 V6/new migration은 0이다.

## 5.2 Clean install

Exact merged-dev Hosted Backend는 pinned `postgres:18.6-alpine3.23` Testcontainer에서 empty database의 Flyway V1→V5, required tables/indexes/constraints와 application startup을 검증했다. Run `32377300451`은 107 total / 107 passed / 0 failed / 0 skipped다. Final evidence head도 같은 Backend semantic evidence를 다시 통과해야 한다.

## 5.3 Released V2 → candidate V5 upgrade

Disposable `postgres:18.6-alpine3.23` container와 temporary non-committed Flyway harness로 다음 순서를 실제 실행했다.

```text
empty PostgreSQL 18.6
→ exact candidate migration source target V2
→ V1/V2 User + Spring Session representative non-secret fixture
→ same source target V5
→ Flyway validate
```

Result:

```text
temporary upgrade test        1 passed / 0 failed / 0 skipped
V2 → V5 migrations executed   3
Flyway history                V1, V2, V3, V4, V5 success
V1/V2 checksums               unchanged
preserved users               1
preserved sessions            1
preserved attributes          1
V3/V4/V5 tables/constraints   present
live database access          none
```

Temporary test source, PostgreSQL tmpfs container, network와 test image는 evidence 확보 뒤 제거했다. Gradle/Node dependency cache만 development cache로 유지하며 repository Product/test source diff는 0이다.

## 5.4 Previous application / forward schema

`main@751a9ee33282e20d46f9356ffecfbc110a692c9c`의 API image를 exact archive에서 build해 disposable V5 database에 연결했다.

```text
classification                 TESTED
PostgreSQL                     18.6
Flyway validation              5 migrations success
database current version       5
main binary latest migration   2
migration action               none required
application startup            PASS
Actuator health / DB            UP / UP
V1/V2 fixture after startup    user 1 / session 1 / attribute 1
```

Flyway는 schema version 5가 main binary의 latest resolved version 2보다 새롭다는 warning을 남겼지만 validation과 startup을 허용했다. 따라서 Phase 1 application rollback may leave additive V3-V5 schema in place는 disposable environment에서 `TESTED`; actual Production data와 deployment rollback은 Gate 4가 별도 검증한다.

# 6. Local and Hosted Validation

Local evidence:

| Area | Result | Evidence |
|---|---|---|
| Frontend | `PASS` | pinned Node 24.19.0, npm ci, lint, typecheck, 5 files / 49 tests, failed/skipped 0, production build, audit finding 0 |
| Compose | `PASS` | `.env.example` render와 existing API/Web Dockerfile image build |
| Backend full check | `NOT RUN — HOST LIMIT` | host Java가 없고 development container Docker socket mount가 global security policy로 금지됨 |
| Upgrade harness | `PASS` | pinned Gradle 9.7.0 / Java 25 container + external disposable PostgreSQL 18.6, 1/1 |

첫 immutable release-evidence head `061309782898eaba4658d99f048849eae667c321`의 run `32381793799` 결과:

```text
Backend                 SUCCESS — 107/107, failed 0, skipped 0
Frontend                SUCCESS — 49/49, failed 0, skipped 0
Infrastructure          SUCCESS
ARM64 Release Artifact  SUCCESS
```

ARM64 job은 existing native `ubuntu-24.04-arm` runner에서 exact PR head를 checkout하고 current backend/frontend Dockerfile을 `linux/arm64`로 build했다. Actual log evidence는 다음과 같다.

```text
runner / uname             ARM64 / aarch64
API image                  architecture=arm64 os=linux
Web image                  architecture=arm64 os=linux
emulation/QEMU             not used — native runner
image publish              none
repository Secret usage    none
Production operation       none
```

Final evidence-sync head에서도 네 job을 다시 통과해야 READY다.

# 7. Recovery Impact

```text
RECOVERY PLAN REQUIRED
```

V3/V4/V5는 released main V1/V2에 `surveys`, `questions`, `question_options`, schema-only `survey_responses`를 추가한다. Exact SQL은 additive create-table/index/constraint이며 V1/V2 table/row를 update/drop/rename하지 않는다. Reverse/down migration은 제공하지 않는다.

Gate 4 / Production activation 전에 actual environment에 적용할 blocking actions:

1. existing live data가 있으면 predeploy PostgreSQL logical backup 생성과 정상성 검증;
2. recovery requirement에 맞는 isolated scratch restore 검증;
3. target retention과 off-host copy policy 확정;
4. disposable V2→V5 rehearsal evidence 재사용 가능성 및 target-specific 차이 확인;
5. previous application / forward V5 schema rollback boundary와 forward-fix 절차 확정;
6. Gate 3-approved artifact의 target deployment와 API/Web/PostgreSQL health acceptance;
7. Production authorization 뒤에만 public smoke 실행.

이 Gate는 live backup, restore, migration, deployment 또는 public smoke를 실행하지 않았으며 operational recovery readiness를 완료로 표시하지 않는다.

# 8. Phase Boundary and Static Audit

Exact candidate audit expectation:

```text
functional public /s/{slug} route         0
public Survey controller/runtime          0
Product survey_responses writer           0
Answer / AnswerOption runtime              0
public submit/idempotency execution        0
Result Dashboard / CSV runtime             0
GHCR publish/deploy workflow               0
Cloudflare/Secret mutation                 0
new migration                              0
Production activation                      0
```

Existing V5 `survey_responses` schema와 read-only COUNT/EXISTS repository는 accepted Phase 2/ADR-0006 content다. Test-only direct fixture inserts는 Product writer가 아니다.

# 9. Review Gate and Conclusion

First evidence head와 local Gate 3 evidence review:

```text
P0            0
P1            0
P2            0
unresolved    0
```

```text
Phase 2 Gate 3 main Release Candidate  PASS
Phase 2 main RC                        READY TO OPEN — EVIDENCE PR MERGE REQUIRED
```

Final exact evidence-sync head의 Backend, Frontend, Infrastructure와 ARM64 Release Artifact, full diff/static/docs/security scan과 review thread가 모두 통과해야 PR을 READY로 전환한다. Evidence PR merge/latest dev verification 전에는 actual Release Issue/PR을 만들지 않는다.
