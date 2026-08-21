---
title: Local Development
status: draft
version: 0.6
last_updated: 2026-08-21
---

# 1. Principle

Host에 Java/Postgres 설치를 강제하지 않고 Docker 기반 실행을 우선한다.

# 2. Scaffold Setup

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up --build --wait
```

기본 endpoint:

```text
Web         http://127.0.0.1:18082
API health  http://127.0.0.1:18081/actuator/health
PostgreSQL  127.0.0.1:15433
```

모든 host port는 loopback-only다. `FORMDOCK_WEB_PORT`, `FORMDOCK_API_PORT`, `FORMDOCK_DB_PORT`로 local conflict를 피할 수 있다.

개발 편의를 위해 frontend dev server/backend Gradle 실행을 host에서 선택적으로 사용할 수 있다. Java/Node를 system-wide로 설치하지 않은 환경은 pinned Docker image를 사용한다.

Vite dev server는 `/api`를 local API로 proxy한다. Browser가 API port를 cross-origin으로 직접 호출하도록 CORS를 완화하지 않는다.

# 3. Environment

`.env`는 repository에 secret 값과 함께 commit하지 않는다.

`.env.example` 제공.

Example credential은 disposable local database 전용이며 shared/production에서 재사용하지 않는다. Creator bootstrap은 기본 `false`이고 API는 Creator 없이도 시작한다.

# 4. Database

PostgreSQL development volume 사용.

Flyway가 schema authority.

PostgreSQL 18 volume은 `/var/lib/postgresql`에 mount한다. `docker compose down`은 container/network만 내리고 volume은 보존하며 `down -v`를 일반 종료에 사용하지 않는다.

# 5. Initial Creator

1. Git-ignored `.env`에서 `FORMDOCK_BOOTSTRAP_ENABLED=true`와 email, plaintext password, display name을 제공한다.
2. Password는 15 Unicode 문자 이상, UTF-8 72 byte 이하여야 하며 example placeholder를 shared/production credential로 재사용하지 않는다.
3. 같은 normalized email이 이미 있으면 아무 값도 바꾸지 않고 no-op한다. 같은 email은 없고 user가 0명일 때만 application이 transaction으로 한 명의 ADMIN을 생성한다.
4. Creator 생성 log와 database row를 확인한 뒤 enable flag를 `false`로 되돌리고 plaintext password를 local environment에서 제거한다.
5. Login smoke는 Web의 same-origin `/api/auth/csrf`에서 token을 받은 뒤 `/api/auth/login`에 제출한다. Password를 shell history나 문서에 직접 기록하지 않고 local test client의 protected environment input을 사용한다.

입력이 일부만 있거나 같은 email 없이 다른 user가 있으면 생성하지 않고 startup을 실패시킨다. Secret 원문은 command output과 log에 남기지 않는다.

# 6. Commands

Backend:

```bash
cd backend
./gradlew clean check
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

Runtime validation from repository root:

```bash
docker compose --env-file .env -f infra/compose.yaml config --quiet
docker compose --env-file .env -f infra/compose.yaml build
docker compose --env-file .env -f infra/compose.yaml up --wait
curl --fail http://127.0.0.1:18081/actuator/health
curl --fail http://127.0.0.1:18082/health
curl --fail http://127.0.0.1:18082/
curl --fail http://127.0.0.1:18082/login
curl --fail http://127.0.0.1:18082/admin
docker compose --env-file .env -f infra/compose.yaml down
```

`/`, `/login`, `/admin`과 nested `/admin/surveys/*`는 같은 SPA가 처리한다. `/`는 `/admin`, `/admin`은 `/admin/surveys`로 이동하며 shared Admin guard가 `/api/auth/me`로 server session을 확인한 뒤에만 list/create/Builder/Preview를 렌더링한다. Browser login smoke는 local-only Creator credential을 password manager 또는 protected environment input에서 입력하고, DevTools/Application storage에 password나 session ID를 복사하지 않는다. Reserved slug는 Admin identity text일 뿐이다. Phase 3-A anonymous Public GET과 Phase 3-B V6/data primitive는 `dev`에 통합됐고 현재 tree에는 Phase 3-C exact Public Response POST가 있다. `/s/:slug` respondent UI는 아직 없으며 Phase 3-C가 user-merged되고 latest `dev` validation을 통과한 뒤 Phase 3-D smoke command를 별도로 추가한다.
