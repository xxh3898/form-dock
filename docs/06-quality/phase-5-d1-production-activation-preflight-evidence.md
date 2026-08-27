# Phase 5-D1 Production Activation Preflight Evidence

## 판정

```text
Phase 5-C2 Remote Artifact       COMPLETE + DEV INTEGRATED
Phase 5-D1 Activation Preflight  PASS — DEV INTEGRATION PENDING
Phase 5-D2 Production Activation NOT AUTHORIZED
Production Activation           NOT AUTHORIZED
```

Issue #91의 read-only authorization만 사용해 target Mac mini의 artifact, runtime, first-activation, private configuration, operation lock, backup risk, Cloudflare와 HomeOps contract를 확인했다. Production resource, Secret, database, backup, Cloudflare와 HomeOps mutation은 수행하지 않았다.

## Machine-readable identity

```text
operationUtc=2026-08-27T03:30:28Z
issue=91
repositoryDevSha=3a802d7cd1121005e9cf892acacbabef2ce39340
repositoryDevTree=4dad695035dabfca552b8b3122cb8859d1d909aa
releaseTag=v0.4.0
releaseTagObject=a5ad64b54e4433160b13af8144b21cd33015eba6
releaseGitSha=1648047645720e67d5e928345c875dc53a93ff0e
releaseTree=630ea7b38a721874a447022493be7e49d9d42905
c2EvidenceSha256=6c5435077c3a411bbf6e4497092dba697c18bb8393856f4b3bd734c64cf7bc05
apiArtifact=ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b
webArtifact=ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551
targetPlatform=linux/arm64
```

Tag ref가 아니라 위 digest refs가 D2 deployment authority다. 두 remote manifest의 digest와 `linux/arm64` platform을 public GHCR metadata에서 read-only로 재검증했다.

## Target Mac mini

```text
targetHostRole=FORMDOCK_PRODUCTION
hostArchitecture=arm64
macosVersion=26.6.2
dockerArchitecture=arm64
dockerEngineVersion=29.6.2
dockerComposeVersion=5.3.1
diskAvailableGiB=57
diskAvailablePercent=27
canonicalCompose=infra/compose.production.yaml
intendedProject=form-dock
intendedWebPort=18082
resourceConflictCount=0
```

Port listener, exact Compose project label, intended container/network/volume name을 확인했다. Existing FormDock Production container/network/volume/state/private config는 0이며 unrelated runtime identity나 private path는 evidence에 기록하지 않았다.

## Activation과 database classification

```text
activationClass=FIRST_ACTIVATION
databaseClass=FRESH_PRODUCTION_DB
previousState=NONE
privateConfigStatus=NOT_CREATED_D2_REQUIRED
operationLockStatus=ABSENT_D2_ACQUIRE
```

존재하는 FormDock Production DB가 없으므로 credential이나 SQL로 내부 data를 조회하지 않았다. D2는 clean Flyway V1→V6 startup과 empty-state health를 검증한다. Existing live data가 없으므로 predeploy backup은 `NOT REQUIRED — FRESH DB`다.

## Private configuration과 operation lock

- Repository 밖 owner-only directory를 mode `700`으로 만들고 env/deployment state는 mode `600`으로 관리한다.
- Compose에는 explicit `--env-file`을 사용한다.
- Configuration revision은 non-secret management identity만 기록하며 Secret bytes/hash를 포함하지 않는다.
- D2 operation lock은 private directory의 atomic `mkdir`로 획득한다.
- Existing lock은 fail closed하고 자동 제거하지 않는다. Stale recovery는 process/state/container operation을 operator가 확인한 뒤 별도 explicit action으로만 수행한다.
- First activation candidate state의 previous identity는 `NONE`이다. Rollback은 PostgreSQL volume을 보존하며 `down --volumes`와 destructive Flyway down migration을 사용하지 않는다.

Actual credential과 private config bytes를 생성하거나 읽지 않았다.

## Backup와 accepted risk

```text
localBackupRootStatus=PARENT_READY_D2_CREATE
backupCadence=DAILY
backupRetentionCompletedSets=7
offHostDurabilityStatus=DEFERRED_ACCEPTED_RISK
currentIndependentOffHostTarget=NONE
firstActivationAllowed=true
```

Local private backup root는 D2에서 생성해야 한다. Independent target 부재는 owner가 first activation에 대해 수용한 risk지만 Production durability, disaster recovery 또는 independent backup PASS가 아니다. Persistent/dogfooding data 이후 physical external disk 또는 mounted NAS hardening과 backup→copy→checksum→restore evidence가 필요하다. 같은 internal disk의 다른 directory/APFS volume은 independent target이 아니며 iCloud Drive는 `INTERIM_SYNC_COPY`만 가능하다.

## Cloudflare

```text
edgeNetworkStatus=READY
edgeAliasConflictCount=0
cloudflaredEdgeStatus=ATTACHED
cloudflareRouteState=ROUTE_ABSENT_DNS_NXDOMAIN
cloudflareOriginAlias=form-dock-web:8080
```

Current external `edge` network와 running containerized cloudflared의 attachment를 allowlisted Docker metadata로 확인했다. D2는 별도 승인 아래 `forms.chochiho.cloud → cloudflared → edge → http://form-dock-web:8080` published hostname을 만든다. Canonical Compose는 Web만 `edge`에 연결하고 API/PostgreSQL은 연결하지 않는다. D1에서는 Docker network, tunnel, DNS와 route를 변경하지 않았다.

## HomeOps monitoring

```text
monitoringProvider=HomeOps
homeOpsRuntimeStatus=READY
serviceHealthAuthority=HOMEOPS_EXACT_HTTPS_URL
operationalIncidentHistory=HOMEOPS
backupDeployEventIngestion=D2_EXPLICIT_HOMEOPS_CONFIGURATION
outboundNotification=DISABLED_BY_OPERATOR_CHOICE
monitorDiskMinimumAvailablePercent=15
monitorBackupMaxAgeSeconds=93600
monitorHttp5xxThreshold=10
monitorHttp5xxWindowSeconds=300
monitorExecutionCadenceSeconds=300
```

HomeOps Web/API/DB의 healthy status만 read-only로 확인했다. D2 service registration, backup/deploy reporter, notification eligibility 변경은 exact HomeOps mutation scope를 별도 승인받아야 한다. FormDock first activation은 global notification switch를 enable하거나 historical incident를 replay하지 않는다.

## Helper와 regression evidence

`infra/production/preflight.sh`은 canonical project/port/release/digest allowlist, target/resource/DNS/HomeOps/artifact observation과 한 개의 evaluation authority를 사용한다. Actual output은 fixed sanitized key/value이며 private path나 Secret content를 출력하지 않는다.

`infra/production/test/preflight-smoke.sh`은 secret-free fixture로 다음을 검증한다.

```text
PASS fixture                         PASS
wrong architecture/port/resource     BLOCKED
unexpected config/state/lock          BLOCKED
missing edge/cloudflared/HomeOps      BLOCKED
unexpected DNS/artifact               BLOCKED
duplicate/unknown fixture field       INVALID
Docker/Cloudflare mutation command    0
Secret/private path output            0
```

Fixture PASS는 actual Mac evidence를 대체하지 않으며 output에 `evidenceMode=fixture`를 명시한다. Exact PR head의 Hosted checks는 PR evidence에서 확인하며 이 point-in-time target evidence의 값으로 재사용하지 않는다.

## D2 required actions와 negative scope

D1 blocker는 0이지만 D2 live action은 아직 승인되지 않았다. D2는 별도 Issue에서 최소 다음 exact mutation을 승인받아야 한다.

```text
private directory/env/state/lock creation
exact digest pull and canonical Compose activation
clean Flyway V1→V6 and first Creator bootstrap
first completed local backup and verification after activation
Cloudflare published hostname to form-dock-web:8080
HomeOps service/reporter registration when explicitly approved
health, same-origin, public Product smoke and state commit
```

```text
remainingD1Blockers=0
productionMutationCount=0
secretValueReadCount=0
databaseWriteCount=0
backupRestoreExecutionCount=0
cloudflareMutationCount=0
homeOpsMutationCount=0
ghcrMutationCount=0
```

## Gate boundary

이 document는 D1 read-only target/contract evidence다. PR merge 전 상태는 `PASS — DEV INTEGRATION PENDING`이고, merge 뒤에도 5-D2, Production deploy/activation, Secret 작업, database/backup, Cloudflare 또는 HomeOps mutation을 자동 승인하지 않는다.
