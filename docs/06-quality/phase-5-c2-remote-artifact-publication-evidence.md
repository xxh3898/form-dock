# Phase 5-C2 Remote Artifact Publication Evidence

## 판정

```text
Phase 5-C1 Delivery/Monitoring   COMPLETE + DEV INTEGRATED
Phase 5-C2 Remote Artifact       PUBLISHED — EVIDENCE DEV INTEGRATION PENDING
Phase 5-D Activation Gate        NOT AUTHORIZED
Production Activation           NOT AUTHORIZED
```

Issue #89의 final decision에 따라 GitHub-hosted native ARM64 runner가 exact Phase 4 release API/Web images를 GHCR에 최초 publish했다. Remote digest를 다시 pull해 current `dev`의 canonical Production Compose와 delivery tooling으로 disposable acceptance했으며 Mac mini와 Production/live resource는 사용하지 않았다.

## Machine-readable identity

아래 fixed fields는 subsequent exact-head workflow가 remote digest를 read-only 비교하는 authority다.

```text
publicationStatus=published
publicationRunId=33027001318
publicationWorkflowHead=13458717882d687e5d0600c7fb61b450ceaf8b4a
publicationUtc=2026-08-27T00:30:43Z
releaseTag=v0.4.0
releaseGitSha=1648047645720e67d5e928345c875dc53a93ff0e
releaseTree=630ea7b38a721874a447022493be7e49d9d42905
targetPlatform=linux/arm64
apiTagRef=ghcr.io/xxh3898/form-dock-api:sha-1648047645720e67d5e928345c875dc53a93ff0e
apiRemoteDigest=sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b
apiLocalImageId=sha256:df3952c439cf559f4de55186bddb3449193a264f3a1964f54ffa51dda107854e
apiDockerfileSha256=810bb2888d989e1d7ee03192c3034e4fb166cb14ef1fc9d738fd5d3740581622
apiPackageVisibility=public
webTagRef=ghcr.io/xxh3898/form-dock-web:sha-1648047645720e67d5e928345c875dc53a93ff0e
webRemoteDigest=sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551
webLocalImageId=sha256:7426bdd7afd649938f3679269fc25b6cac8bf9ab0d9412ae341e99e59d1624a5
webDockerfileSha256=d96296c8d27d964703da1c4685e6ea5c49f5393214e1ecae5797462f9a2b0353
webPackageVisibility=public
```

Package visibility는 publication 뒤 read-only로 관찰한 값이다. Workflow나 operator가 visibility를 변경하지 않았다.

## Source와 publication provenance

| 항목 | 결과 |
| --- | --- |
| Issue / operation UTC | #89 / 2026-08-27 |
| Annotated tag object | `a5ad64b54e4433160b13af8144b21cd33015eba6` |
| Tag peeled target | `1648047645720e67d5e928345c875dc53a93ff0e` |
| Release tree | `630ea7b38a721874a447022493be7e49d9d42905` |
| Build host | GitHub-hosted `ubuntu-24.04-arm` / native `aarch64` |
| Image platform | API/Web `linux/arm64` |
| Exact-tag collision preflight | PASS — 두 approved full-SHA tags 없음 |
| Publication mode | `published` |
| Approved tag push | API 1 / Web 1 |
| Moving alias push | 0 |
| Remote overwrite/delete | 0 |
| Visibility mutation | 0 |

Build context는 workflow tooling head가 아니라 별도 clean checkout의 exact release SHA다. Runtime assertion으로 annotated tag type/target, release tree, clean status, runner architecture를 확인했고 API/Web Dockerfile SHA-256을 OCI metadata와 remote image config에서 다시 비교했다.

## Remote digest와 disposable acceptance

Hosted run [`33027001318`](https://github.com/xxh3898/form-dock/actions/runs/33027001318)의 `GHCR Publication Evidence` job 결과다.

```text
remote API digest verified        PASS
remote Web digest verified        PASS
remote OCI source identity        PASS
pull by digest                    PASS
canonical Production Compose      PASS
PostgreSQL health                 PASS
API health                        PASS
Web health                        PASS
same-origin Web → API             PASS
PostgreSQL/API host exposure      0
Web host exposure                 127.0.0.1 only
network topology                  PASS
bounded log rotation              PASS
Flyway history                    1,2,3,4,5,6
failed Flyway migration           0
container/network/volume/temp residue 0
```

Acceptance project는 unique `dev-form-dock-delivery-published-*` 이름, repository 밖 mode-600 env/state와 disposable PostgreSQL volume만 사용했다. Tag ref가 아니라 recorded remote digest ref를 deployment state의 image reference로 사용하고 actual pulled local image ID를 별도로 기록했다.

## Hosted regression

First publication head `13458717882d687e5d0600c7fb61b450ceaf8b4a`의 Validate run `33027001318`:

```text
Backend                    SUCCESS — 171/171, failed 0, skipped 0
Frontend                   SUCCESS — 11 files / 104 tests
Infrastructure             SUCCESS
GHCR Publication Evidence  SUCCESS — mode=published
ARM64 Release Artifact     SKIPPED — exact #89 publication job이 native ARM64 authority 소유
```

이 evidence commit의 exact-head rerun은 `mode=verified-existing`과 `evidenceStatus=matched`여야 한다. 기존 exact tag를 다시 push하지 않으며 recorded tag가 없거나 digest와 remote identity가 다르면 fail closed한다.

## Security와 negative scope

```text
ephemeral github.token              사용 — exact publication job only
PAT/repository Secret               0
credential bytes in log/evidence    0
workflow-wide packages:write        0
Mac mini image build/push/pull      0
Production Compose deploy/restart   0
Production env/Secret               0
live DB/backup/restore/migration     0
Cloudflare/public routing           0
GitHub Release/tag mutation         0
Product/API/Flyway/schema diff      0
```

## Gate boundary

Remote artifact publication과 Hosted disposable acceptance는 Phase 5-C2 evidence다. 이 PR의 merge 전에는 evidence가 `dev` Source of Truth에 통합되지 않았으며, merge 뒤에도 Phase 5-D, target Mac mini pull/deploy, Production configuration/Secret, live database action, Cloudflare와 public smoke를 승인하지 않는다.
