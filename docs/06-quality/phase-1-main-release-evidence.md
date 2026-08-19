---
title: Phase 1 Main Release Candidate Evidence
status: active
version: 1.0
last_updated: 2026-08-19
---

# 1. Gate 3 판정 경계

이 문서는 Issue #16의 release-evidence candidate를 기준으로 Phase 1 Gate 3를 검증한다. 다음 상태는 evidence PR이 `dev`에 merge되고 최신 merged `dev`가 별도로 검증된 뒤에만 효력이 생긴다.

```text
Phase 1 Creator Foundation      COMPLETE
Gate 3 full release diff        PASS
Gate 3 ARM64 artifact build     PASS
Gate 3 Flyway compatibility     PASS
Gate 3 recovery classification  RECOVERY PLAN REQUIRED
Phase 1 main RC                 READY TO OPEN — evidence PR merge/latest dev verification required
Survey Domain / Phase 2         NOT AUTHORIZED
Production                      NOT AUTHORIZED
```

`READY TO OPEN`은 별도 `dev → main` Release Issue/PR을 만들 수 있다는 뜻이다. 이 evidence PR 자체가 release PR, Production readiness 또는 Phase 2 authorization을 만들지는 않는다.

# 2. Exact Repository Baseline

```text
main SHA              80b602f3e83d8eae0bdcbe9e28512398c86681d5
dev baseline SHA      9d29f47165968b0308ea7b3e90c2183fe68cc48c
first evidence head   a7dffb66401bf9169bfa5ae30a74e00089a91cad
merge base            80b602f3e83d8eae0bdcbe9e28512398c86681d5
main ancestor         PASS
candidate commits     22
changed files         120
diff summary          10,103 insertions / 239 deletions
```

문서는 자신의 최종 commit SHA와 그 commit이 생성할 미래 Hosted run ID를 같은 commit 안에 literal로 담을 수 없다. 따라서 repository 문서에는 첫 불변 ARM64 evidence head/run을 기록하고, 최종 review head와 exact-head run은 PR 본문과 workflow job summary에 기록한다. 최종 review는 PR의 latest head만을 대상으로 한다.

`dev` baseline까지의 first-parent integration boundary는 다음 9개다.

| Boundary | Merge SHA |
|---|---|
| Phase 0 contracts — PR #2 | `403be72e840b26f0b0b8247be338e70ed1c2ddf4` |
| Application scaffold — PR #3 | `ff90fe631fbde7966fd84b14a2957d08f933be7d` |
| Phase 1 entry — PR #4 | `af96baa3b0bc23b78375368e6249379cfc8ff3bc` |
| Creator persistence — PR #5 | `14b049dcaa1b43b138a0c60258e2fc887658b1f5` |
| Creator session authentication — PR #8 | `ec816fb6a64fd32107ee267a0a7754d67621a4a2` |
| Repository governance — PR #9 | `4c55d23a72950b36eaffcae9ad9f5a5b63b479b1` |
| Creator Login/Admin shell — PR #11 | `3aee824c6abb6a8ccc2cc6348d887fa1e7d359ad` |
| Phase 1 completion — PR #13 | `05309f7976dfb3fd417a82fdfb3014379b02db9d` |
| Gate ownership — PR #15 | `9d29f47165968b0308ea7b3e90c2183fe68cc48c` |

# 3. Full Release Diff Inventory

`main...candidate`는 다음 release areas를 포함한다.

- Phase 0 product/domain/architecture/data/API/quality/operations contracts와 accepted ADR
- Java 25 / Spring Boot 4 backend scaffold와 Creator persistence, bootstrap, session authentication
- React / TypeScript / Vite frontend scaffold와 최소 Login/Admin shell
- PostgreSQL 18 development runtime, Docker Compose, API/Web Dockerfile와 health baseline
- repository governance templates와 Hosted validation workflow
- Gate 3 native ARM64 artifact validation과 이 evidence

현재 runtime API route는 `/api/auth/csrf`, `/api/auth/login`, `/api/auth/logout`, `/api/auth/me`다. Versioned migration은 다음 두 개뿐이다.

```text
V1__create_users.sql
V2__create_spring_session.sql
```

Release diff audit 결과:

```text
conflict markers                         0
git diff --check failures                0
unexpected Secrets/credentials           0
Survey/Phase 2 runtime implementation    0
Production/deployment activation         0
active contract contradictions           0
```

# 4. Hosted Gate Evidence

첫 ARM64 evidence는 PR #17의 exact head `a7dffb66401bf9169bfa5ae30a74e00089a91cad`에 연결된 Hosted run [32264634599](https://github.com/xxh3898/form-dock/actions/runs/32264634599)다.

| Job | Result | Evidence |
|---|---|---|
| Backend | `success` | 44 tests `PASSED`, failed/skipped 0; PostgreSQL 18.6 Testcontainer와 clean Flyway V1/V2 migration 포함 |
| Frontend | `success` | lint, typecheck, 17/17 Vitest tests와 production build |
| Infrastructure | `success` | Compose config와 existing API/Web Dockerfile build |
| ARM64 Release Artifact | `success` | native `ubuntu-24.04-arm`; existing API/Web Dockerfile를 `linux/arm64`로 build하고 metadata 검사 |

ARM64 job의 inspected result:

```text
form-dock API image  architecture=arm64  os=linux
form-dock Web image  architecture=arm64  os=linux
emulation/QEMU       not used — native ARM64 runner
image publish        no
repository Secret    no
Production operation no
```

Backend regression은 disposable PostgreSQL만 사용했다. Live database connection, migration, backup 또는 restore는 실행하지 않았다. 이 문서 뒤의 최종 evidence commit도 동일한 네 job을 exact-head에서 다시 통과해야 review-ready다.

# 5. Recovery Impact

```text
RECOVERY PLAN REQUIRED
```

현재 `main`에는 V1/V2가 없고 candidate가 application/session schema를 도입하므로 Gate 4 / Phase 5는 Production activation 전에 적용 가능한 다음 action을 실제 환경 기준으로 완료해야 한다.

1. isolated scratch database에서 logical backup restore 검증
2. existing live data가 있으면 deploy 전 logical backup 생성과 정상성 검증
3. target environment에 적용할 retention과 off-host copy policy 확인
4. previous application과 forward-only schema 사이의 recovery boundary 및 필요 시 forward-fix 절차 확정
5. Gate 3-approved artifact의 target deployment와 API/Web/PostgreSQL health acceptance

이 action은 이름만 Gate 3에서 고정한다. 이 Issue에서는 어느 것도 실행하거나 완료로 표시하지 않는다.

# 6. Review Gate

최종 exact-head Hosted validation과 review가 모두 끝난 뒤 다음 값이 유지되어야 한다.

```text
P0            0
P1            0
P2            0
unresolved    0
```

Evidence PR merge와 latest merged `dev` verification 전에는 Phase 1 main RC의 effective status가 계속 `BLOCKED`다. Production과 Survey Domain / Phase 2도 계속 `NOT AUTHORIZED`다.
