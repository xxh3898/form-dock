---
title: Monitoring
status: draft
version: 0.2
last_updated: 2026-08-26
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

# 6. Phase 5 Ownership

Phase 5-C는 tool-neutral monitoring/log rotation/health acceptance와 alert delivery contract를 확정한다. Netdata, Uptime Kuma 또는 외부 SaaS를 Product requirement로 자동 선택하지 않는다.

Phase 5-D는 별도 live-operation 승인 뒤 실제 target에서 다음을 확인한다.

- Postgres `pg_isready`, API `/actuator/health`, Web `/health`
- Cloudflare/public Web과 same-origin Web→API
- 대표 anonymous Public Survey/Response와 Creator login/Admin/Results smoke
- backup failure, disk low와 repeated 5xx alert path

Monitoring 도구 설치·설정 변경, notification credential과 public endpoint mutation은 Phase 5 Entry 또는 5-A/5-B 권한에 포함되지 않는다.
