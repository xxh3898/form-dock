---
title: Local Development
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Principle

Host에 Java/Postgres 설치를 강제하지 않고 Docker 기반 실행을 우선한다.

# 2. Expected Scaffold Setup

```text
docker compose up
```

개발 편의를 위해 frontend dev server/backend Gradle 실행을 host에서 선택적으로 사용할 수 있다.

Vite dev server는 `/api`를 local API로 proxy한다. Browser가 API port를 cross-origin으로 직접 호출하도록 CORS를 완화하지 않는다.

# 3. Environment

`.env`는 repository에 secret 값과 함께 commit하지 않는다.

`.env.example` 제공.

# 4. Database

PostgreSQL development volume 사용.

Flyway가 schema authority.

# 5. Initial Creator

1. repository에 값이 없는 bootstrap variable 이름만 `.env.example`에 문서화한다.
2. local secret file에서 bootstrap enable flag, email, plaintext password, display name을 제공한다.
3. 같은 normalized email이 이미 있으면 아무 값도 바꾸지 않고 no-op한다. 같은 email은 없고 user가 0명일 때만 application이 transaction으로 한 명의 ADMIN을 생성한다.
4. login을 확인한 뒤 bootstrap enable flag와 plaintext password를 local environment에서 제거한다.

입력이 일부만 있거나 같은 email 없이 다른 user가 있으면 생성하지 않고 startup을 실패시킨다. Secret 원문은 command output과 log에 남기지 않는다.

# 6. Commands

구체 Gradle/npm/Compose 명령은 repository scaffold 이후 갱신한다.
