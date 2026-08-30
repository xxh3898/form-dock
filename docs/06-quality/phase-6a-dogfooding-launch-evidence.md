# Phase 6-A Dogfooding Launch Evidence

## 판정

```text
Phase 6-A                    COMPLETE
Dogfooding collection        ACTIVE
Real Survey                  OPEN
Real external responses      COLLECTION PENDING
Phase 6                      IN PROGRESS
FormDock V1                  INCOMPLETE
```

이 판정은 Issue #97이 승인한 Cubing Hub V2.3 검증 Survey 한 건에만 적용한다. Phase 6-B export/analysis, Phase 6 전체 또는 FormDock V1 완료 권한이 아니다.

## Repository와 Production identity

2026-08-30 KST revalidation 기준:

```text
repository dev SHA           913abf6b0e0d8f81293809dc57a2e089dde93687
repository dev tree          ab70ad922c62c0565423aaf3b26534c67867437a
open conflicting PR          0

Production release           v0.4.0
application revision         1648047645720e67d5e928345c875dc53a93ff0e
platform                     linux/arm64
Flyway                       V1..V6 / failure 0
PostgreSQL/API/Web           HEALTHY
bootstrap                    DISABLED / credential fields EMPTY
ADMIN Creator                exactly 1
API/PostgreSQL host ports    0
operation lock               ABSENT / RELEASED
```

Accepted API/Web digests는 [Phase 5-D2B evidence](phase-5-d2b-public-homeops-activation-evidence.md)와 일치했다. Application image, schema, runtime config와 route는 변경하지 않았다.

## Survey definition과 canonical parity

Authority는 Issue #97 실행 시 사용자가 제공한 exact questionnaire와 operator-selected slug `cube`다. Repository requirement를 질문으로 변환하거나 문구를 임의로 변경하지 않았다.

```text
Survey ID                    3
slug                         cube
status                       OPEN
Questions                    17
Choice Options               80
required / optional          14 / 3
Question positions           0..16 exact
Response count               0
semantic parity              PASS
semantic SHA-256             1467e6f9e6b38ae080977e89a9ea79918dc84ae5e9bb98504900bb6af2224fb1
```

Canonical Public Survey DTO에서 server-owned ID를 제외하고 title, slug, description, privacy notice, Question type/title/description/required/position, Choice label/order와 Scale configuration을 trusted input과 비교했다. Q1~Q17 전체가 exact match였다.

Question type count:

```text
SINGLE_CHOICE                9
MULTIPLE_CHOICE              3
SCALE                        2
LONG_TEXT                    2
SHORT_TEXT                   1
```

## Public collection handoff

```text
public URL                   https://forms.chochiho.cloud/s/cube
public page                  HTTP 200
Public Survey API            HTTP 200
anonymous auth requirement   NONE
launch browser smoke         PASS
synthetic Response POST      0
```

Launch operation에서 title, 시작 동작과 Q1 선택지 5개가 actual browser에 렌더되는 것을 확인했다. 이번 repository revalidation에서는 unchanged accepted runtime과 exact semantic hash를 다시 확인했으며 실제 연구 데이터의 purity를 위해 Response POST를 실행하지 않았다.

## Operations

```text
FormDock monitored service   ENABLED / HEALTHY
open FormDock incident       0
service notification         false
global notifications         false
off-host durability          DEFERRED_ACCEPTED_RISK
```

HomeOps, Cloudflare, GHCR, Secret, backup과 Production runtime configuration을 변경하지 않았다.

## Mutation accounting

Phase 6-A launch operation:

```text
Survey                       +1
Question                     +17
Question Option              +80
SurveyResponse               +0
Answer / AnswerOption        +0 / +0
```

이번 evidence reconciliation:

```text
Product/Response mutation    0
DB/schema/Flyway mutation    0
application/deploy mutation  0
GHCR/Cloudflare/HomeOps      0 / 0 / 0
Secret exposure              0
```

## 남은 Gate

- 실제 외부 Response의 충분성 기준과 수집 상태 확인
- real dataset CSV export와 실제 분석 usability 검증
- contact field personal data를 privacy notice 경계 안에서 처리
- operational finding 기록과 triage
- independent off-host durability hardening 결정

위 항목은 별도 Phase 6-B authorization 전에는 수행하지 않는다.
