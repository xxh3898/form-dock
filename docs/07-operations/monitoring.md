---
title: Monitoring
status: draft
version: 0.1
last_updated: 2026-08-18
---

# 1. Health

- Postgres `pg_isready`
- API `/actuator/health`
- Web `/health`

# 2. External

Cloudflare/public endpoint smoke.

# 3. Logs

Docker stdout/stderr + rotation.

민감 정보/응답 원문을 application log에 남기지 않는다.

# 4. Metrics

초기에는 health/uptime 중심.

고급 observability stack은 V1 필수 아님.

# 5. Alert Scope

- service down
- DB unhealthy
- backup failure
- disk low
- repeated 5xx

구체 notification channel은 Production Readiness Phase까지 deferred한다. Health endpoint와 application scaffold를 막지 않는다.
