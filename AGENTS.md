# FormDock Repository Contract

## Source of Truth

작업 전 다음 문서를 순서대로 확인한다.

1. `docs/00-product`
2. `docs/01-domain`
3. `docs/02-requirements`
4. `docs/03-architecture`
5. `docs/04-data`
6. `docs/05-api`
7. `docs/06-quality`
8. `docs/07-operations`
9. `docs/08-decisions`

`accepted` ADR과 현재 source of truth를 구현보다 먼저 확인한다. `draft` 또는 `proposed` 문서를 승인된 결정으로 간주하지 않는다.

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

## Current Gate

```text
Phase 0 — Foundation & Contracts
Application implementation authorization = NO
```

별도 승인된 scaffold 세션 전에는 application scaffold를 만들지 않는다. 현재 단계에서는 명시적 승인 없이 scaffold 이상의 기능을 구현하지 않는다.
