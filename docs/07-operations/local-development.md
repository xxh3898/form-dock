---
title: Local Development
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Principle

Host에 Java/Postgres 설치를 강제하지 않고 Docker 기반 실행을 우선한다.

# 2. Candidate Setup

```text
docker compose up
```

개발 편의를 위해 frontend dev server/backend Gradle 실행을 host에서 선택적으로 사용할 수 있다.

# 3. Environment

`.env`는 repository에 secret 값과 함께 commit하지 않는다.

`.env.example` 제공.

# 4. Database

PostgreSQL development volume 사용.

Flyway가 schema authority.

# 5. Initial Creator

bootstrap 절차 `TBD`.

# 6. Commands

구체 Gradle/npm/Compose 명령은 repository scaffold 이후 갱신한다.
