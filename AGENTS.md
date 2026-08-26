# FormDock Repository Contract

## Source of Truth

작업 전 `docs/` 전체와 관련 ADR을 확인한다. 같은 decision scope에서 문서가 충돌하면 다음 우선순위를 적용한다.

1. 명시된 범위 안의 `accepted` ADR
2. `docs/00-product`의 V1 scope와 PRD
3. `docs/01-domain`의 invariant와 lifecycle
4. `docs/02-requirements`의 사용자·기능 요구사항
5. `docs/03-architecture`, `docs/04-data`, `docs/05-api`의 구현 contract
6. `docs/06-quality`, `docs/07-operations`의 검증·운영 contract

ADR은 자신의 architecture decision 범위에서만 우선하며 product scope를 임의로 바꾸지 않는다. `draft` 또는 `proposed` ADR을 승인된 결정으로 간주하지 않는다. 충돌이 계속되면 구현하지 말고 보고한다.

## Language and Localization

- FormDock V1의 기본 사용자 언어는 한국어이며 단일 locale은 `ko-KR`이다.
- Issue, PR, project documentation과 사용자에게 보이는 UI 문구는 자연스러운 한국어를 기본으로 작성한다.
- Commit message는 Conventional Commit prefix를 영어로 유지하고 subject와 body 설명은 한국어를 기본으로 작성한다.
- Code identifier, API path/field/error code, database identifier, enum wire value, 기술 고유명사와 실행 명령은 compatibility와 명확성을 위해 영어를 유지한다.
- 번역을 이유로 API, database schema 또는 domain wire contract를 변경하지 않는다.

## Technology

- Java 25
- Spring Boot 4
- Gradle
- React
- TypeScript
- Vite
- PostgreSQL 18
- Flyway
- Docker Compose

## Rules

- 구현 편의를 위해 문서 contract를 임의로 바꾸지 않는다.
- Production schema 변경은 Flyway migration만 사용한다.
- Secret, credential, token, 실제 환경 값을 commit하지 않는다.
- Application code와 docs가 충돌하면 즉시 작업을 중단하고 보고한다.
- 요청 scope 밖 refactor와 formatting을 하지 않는다.
- Database와 public API 변경 시 compatibility를 검토한다.
- 관련 test와 validation 없이 완료를 선언하지 않는다.
- `main`에서 직접 개발하지 않는다.
- Force push를 하지 않는다.
- 기능 구현 시 관련 docs를 같은 변경 범위에서 동기화한다.

## Development Governance

기본 delivery loop는 다음과 같다.

```text
GPT가 latest dev와 current Phase gate를 읽고 Issue 작성
→ Codex가 Issue contract를 구현·검증해 READY PR 작성
→ Hosted CI와 READY gate 확인
→ GPT가 exact PR head와 evidence review
→ 사용자가 feature/governance PR을 dev에 merge
→ GPT가 merge된 dev exact SHA와 CI 확인
→ GPT가 completed Issue close
→ GPT가 merge된 dev에서 다음 Issue 결정
```

- 기본적으로 active implementation/governance slice는 하나만 둔다.
- `Issue 1 → work branch 1 → PR 1 → dev`를 기본 단위로 사용한다.
- Issue는 scope와 implementation authorization contract이고 PR은 그 Issue의 구현·문서·검증 evidence다.
- 일반 feature/fix/docs/chore `→ dev` PR은 `Related Issue: #N`으로 관계만 기록하고 GitHub closing keyword의 automatic close를 workflow contract로 사용하지 않는다.
- Issue completion close는 사용자 merge와 merged dev exact SHA/CI 확인 뒤 수행하며 merge 또는 Green CI보다 앞서지 않는다.
- 한 PR로 안전하게 review할 수 없는 Issue는 coding 전에 분리한다.
- Codex는 Roadmap scheduling을 authorization으로 해석하거나 현재 Phase authorization을 확대하지 않는다.
- `.github/ISSUE_TEMPLATE`과 `.github/pull_request_template.md`는 `dev`에만 있을 때도 GPT/Codex가 따르는 normative body structure다. GitHub chooser/auto-fill은 해당 파일이 default branch에 도달한 뒤 활성화된다.
- Template은 scope와 evidence 형식을 표준화할 뿐 accepted ADR, Product scope, Domain invariant 또는 Current Gate보다 우선하지 않는다.
- `dev → main`은 Phase 또는 vertical capability release boundary이며 일반 feature PR과 분리한다.
- `main` release merge commit을 `dev` ancestry로 동기화하는 PR은 반드시 GitHub의 **Create a merge commit**으로 통합한다. Squash merge와 rebase merge는 동기화할 ancestry를 제거하므로 해당 PR에서 금지한다.
- Production deployment, migration execution, Secret 작업과 live activation은 release와도 분리된 별도 Gate다.

## Current Gate

```text
Phase 0                          COMPLETE
Application Scaffold            COMPLETE
Phase 1 Creator Foundation       COMPLETE + RELEASED
Phase 2 Survey Builder           COMPLETE + RELEASED
Phase 3 Public Survey/Response   COMPLETE + RELEASED
Phase 4 Results / Export         COMPLETE + RELEASED — v0.4.0
Phase 4 Gate 3                   PASS + RELEASED
Phase 5 Production Readiness     AUTHORIZED — repository/readiness slices only
Production Activation           NOT AUTHORIZED
GitHub Release                   NOT REQUIRED / NOT CREATED
```

Creator/User persistence, one-time bootstrap, Spring Session JDBC schema, login/logout/current Creator, Creator-only Admin protection과 최소 Login/Admin shell은 `main`에 release됐다. Phase 2-A Survey DRAFT Core, Phase 2-B Question/Lock Data Foundation, Phase 2-C Question mutation/lifecycle/deep duplicate backend와 Phase 2-D authenticated Builder/Admin-only Preview도 [Phase 2 Completion Evidence](docs/06-quality/phase-2-completion-evidence.md)와 [Phase 2 Main Release Evidence](docs/06-quality/phase-2-main-release-evidence.md)의 Gate 3 `PASS` tree 그대로 `main`에 release됐다. 이 release는 Production activation이 아니다.

Phase 3-A exact anonymous Public Survey GET, Phase 3-B V6 Response data/canonicalization foundation, Phase 3-C atomic Public Response POST와 Phase 3-D `/s/:slug` respondent frontend는 [Phase 3 Completion Evidence](docs/06-quality/phase-3-completion-evidence.md)와 [Phase 3 Main Release Evidence](docs/06-quality/phase-3-main-release-evidence.md)의 검증을 거쳐 PR #60으로 `main`에 release됐다. Annotated tag `v0.3.0`은 이 repository Release의 identity이며 Production 배포 또는 activation 증거가 아니다.

Phase 4-A Response read backend, 4-B bounded summary backend, 4-C CSV backend와 4-D Admin Results frontend는 [Phase 4 Completion Evidence](docs/06-quality/phase-4-completion-evidence.md)의 exact `dev` 통합·application smoke와 [Phase 4 Main Release Evidence](docs/06-quality/phase-4-main-release-evidence.md)의 full diff, native ARM64, same V1→V6 compatibility 및 `NO DATA/SCHEMA IMPACT` 검증을 통과했다. PR #79의 release merge로 exact tree가 `main`에 반영됐고 annotated `v0.4.0`은 Phase 4 repository Release identity다. GitHub Release는 필요하지 않아 생성하지 않았으며 tag와 `main` Release는 Production 배포 또는 activation 증거가 아니다.

Phase 5는 `5-A Production Runtime Foundation → 5-B Backup/Restore/Recovery Readiness → 5-C Delivery/Monitoring Readiness → 5-D Production Activation Gate` 순서로 한 번에 하나씩 진행한다. 이 Entry가 `dev`에 merge되고 exact checks가 확인된 뒤에는 5-A Issue 하나만 시작할 수 있으며 5-B/5-C는 선행 slice 완료 전까지 pending이다. Remote artifact publish, Secret 작업, live DB/backup/restore, Cloudflare와 Production activation은 각 Issue의 별도 명시 승인 없이는 수행하지 않는다. Phase 5-D와 Production Activation은 현재 승인되지 않았다.
