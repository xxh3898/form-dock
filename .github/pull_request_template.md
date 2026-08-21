## 관련 Issue

Related Issue: #

<!--
일반 feature/fix/docs/chore PR은 dev를 대상으로 한다.
→ dev PR에서는 Closes/Fixes의 자동 Issue 종료에 의존하지 않는다.
사용자가 PR을 merge한 뒤 GPT가 merge된 dev 상태를 확인하고,
완료된 Issue를 종료한 다음 후속 Issue를 만든다.
-->

## Phase

<!-- latest dev Source of Truth 기준 현재 Product Phase를 기록한다. -->

## 승인 근거

<!-- AGENTS.md Current Gate, Roadmap, 관련 accepted ADR과 merge된 선행 조건을 인용한다. Roadmap 일정만으로 승인됐다고 판단하지 않는다. -->

## 요약

<!-- 이 PR의 관찰 가능한 결과를 설명한다. -->

## 포함 범위

-

## 명시적 제외 범위

-

## 구현

<!-- 가장 작은 일관된 구현과 보존한 invariant를 설명한다. -->

## 검증

실제로 실행한 검증만 기록한다. 근거가 있는 것처럼 표현하지 말고 `NOT RUN — 이유` 또는 `N/A — 이유`를 사용한다.

### Backend

<!-- 기준 명령: cd backend && ./gradlew --no-daemon clean check -->

- 결과:
- 근거:

### Frontend

<!-- 기준 명령: cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build -->

- 결과:
- 근거:

### PostgreSQL / Testcontainers

- 결과:
- 근거:

### Flyway

- 결과:
- 근거:

### Compose

<!-- 기준 명령: docker compose --env-file .env.example -f infra/compose.yaml config --quiet && docker compose --env-file .env.example -f infra/compose.yaml build -->

- 결과:
- 근거:

### Hosted CI

- Head SHA:
- Backend:
- Frontend:
- Infrastructure:
- Run:

### 문서

- `git diff --check`:
- UTF-8 / LF / final newline:
- Markdown fences / local links:
- Contract contradiction scan:

## Contract 영향

<!-- Product, domain, API, architecture, quality 또는 operations contract 변경을 기록한다. 없으면 N/A — 이유를 사용한다. -->

## 보안 / Session / CSRF

<!-- 개인정보, credential, origin과 authorization 영향을 포함한다. 없으면 N/A — 이유를 사용한다. -->

## Data / Migration

<!-- schema compatibility, Flyway, backup, rollback과 data-loss 영향을 포함한다. 없으면 N/A — 이유를 사용한다. -->

## Production 영향

<!-- deployment, Secret, live data 또는 public URL 영향을 기록한다. Production 작업은 별도 Gate가 필요하다. -->

## 위험

<!-- 남은 위험과 아직 증명하지 못한 항목을 기록한다. -->

## Rollback / Recovery

<!-- 파괴적인 shortcut 없이 안전한 code/config/data 복구 경계를 기록한다. -->

## 후속 작업

<!-- 미룬 작업을 연결하거나 NONE — 이유를 기록한다. -->

## 다음 승인 Slice

<!-- 현재 Roadmap과 Phase gate가 승인한 경우에만 다음 slice를 기록한다. 아니면 NOT AUTHORIZED를 사용한다. -->
