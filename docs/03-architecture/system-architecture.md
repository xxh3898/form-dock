---
title: System Architecture
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Overview

```text
Internet
→ Cloudflare
→ form-dock-web
→ form-dock-api
→ form-dock-postgres
```

# 2. Components

## Web

React + TypeScript + Vite.

Production에서는 static asset container로 제공.

## API

Java 25 + Spring Boot 4.

Responsibilities:

- auth/session
- authorization
- Survey domain
- Response validation
- aggregation
- CSV

## PostgreSQL

PostgreSQL 18.

Public internet 비노출.

# 3. Deployment

Mac mini Docker Compose.

서비스는 Cubing Hub와 별도 Compose project로 운영한다.

# 4. Boundaries

Admin API와 Public API namespace를 분리한다.

```text
/api/auth
/api/surveys
/api/public/surveys
```

# 5. Non-goals

- microservices
- event bus
- Kubernetes
- external managed DB
