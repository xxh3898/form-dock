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
- Production deployment, migration execution, Secret 작업과 live activation은 release와도 분리된 별도 Gate다.

## Current Gate

```text
Phase 0                          COMPLETE
Application Scaffold            COMPLETE
Phase 1 Creator Foundation       AUTHORIZED
Survey Domain Implementation    NOT AUTHORIZED
```

현재 승인된 business 범위는 Creator/User persistence, one-time bootstrap, Spring Session JDBC schema, login/logout/current Creator, Creator-only Admin protection과 최소 Login/Admin shell이다. Survey, Question, Response, Result와 CSV는 별도 승인 전 구현하지 않는다.
