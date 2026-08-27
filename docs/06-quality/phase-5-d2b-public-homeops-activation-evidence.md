# Phase 5-D2B Public/HomeOps Final Activation Evidence

## 판정

```text
Phase 5-D1 Activation Preflight  COMPLETE + DEV INTEGRATED
Phase 5-D2A Local Bootstrap      COMPLETE + DEV INTEGRATED
Phase 5-D2B Public/HomeOps       LIVE ACTIVE + ACCEPTED
Production Activation           ACTIVE + ACCEPTED
Public Route                     ACTIVE + ACCEPTED
HomeOps Integration              ACTIVE + ACCEPTED
Phase 6 Dogfooding               NOT AUTHORIZED
```

Issue #95의 explicit Production Operations Gate에 따라 exact `v0.4.0` local Production을 보존한 채 pre-public backup, FormDock 전용 Cloudflare route, public transport/security와 Product canary, existing HomeOps integration을 순서대로 검증했다. Application image, schema, GHCR와 notification 설정은 변경하지 않았다.

## 고정 근거 식별자

```text
acceptanceCompletedAt=2026-08-27T15:17:44Z
issue=95
repositoryEvidenceBaseSha=78db5f62a6714fc8125546dc5d9c0019078e5a88
repositoryEvidenceBaseTree=902a160c0937a180407bc1b434fdc0c658456cdf
releaseTag=v0.4.0
releaseGitSha=1648047645720e67d5e928345c875dc53a93ff0e
releaseTree=630ea7b38a721874a447022493be7e49d9d42905
apiArtifact=ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b
webArtifact=ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551
targetPlatform=linux/arm64
finalMergeGateAuthority=GITHUB_PR_REQUIRED_CHECKS
requiredChecks=Backend,Frontend,Infrastructure
```

이 evidence는 특정 시점의 Production acceptance를 기록한다. PR 번호, latest Hosted run ID와 Ready/merge 순간 상태를 canonical field로 고정하지 않으며 repository integration의 최종 authority는 GitHub PR의 현재 required checks와 merged `dev` exact SHA/tree다.

## Read-only 정합 확인

```text
formDockContainerHealth=POSTGRES_API_WEB_HEALTHY
formDockArtifactIdentity=EXACT_V0_4_0_DIGESTS
formDockFlyway=V1_TO_V6_FAILURE_0
creatorAdminCount=1
bootstrapFinalRuntime=DISABLED_EMPTY
webBind=127.0.0.1:18082_ONLY
apiHostPortCount=0
postgresHostPortCount=0
webEdgeAlias=form-dock-web
apiEdgeAttachment=ABSENT
postgresEdgeAttachment=ABSENT
homeOpsAcceptedRevision=950a0ec47ee7099fc7d525838748b06a96121c61
homeOpsApiDigest=sha256:9d43a0b8506783c1995108dbfbac5708989ab8bf76f65baf33fbb205c55bd8c5
homeOpsWebDigest=sha256:8bb8eb7532f37058404d99099b707b008c6f843fb6dc958e9c6e510e1d7ff0c8
homeOpsRuntimeConfigDigest=sha256:914e44c79a6054d5b70070e484a3e7b40d0d0d2f04320b16c6e3c4de03a3cdf1
homeOpsFlyway=V13_FAILURE_0
homeOpsSignalReporter=SUPPORTED
previousSignalBlocker=RESOLVED
```

HomeOps #102→#104→#106→#108 이전의 `DISK_LOW`/`HTTP_5XX_BURST` interface 부재 finding은 historical evidence로만 분류했다. Installed revision, API/Web/runtime-config digest, Flyway V13과 reporter `signal` mode가 accepted identity와 일치하므로 source extension을 반복하지 않았다.

## Pre-public backup와 운영 안전성

```text
operationLock=ACQUIRED_THEN_RELEASED
backupCreatedAt=2026-08-27T14:58:28Z
backupFormat=POSTGRES_CUSTOM
backupChecksum=PASS
backupMetadata=PASS
backupReadability=PASS
completedBackupSetCountAfter=2
preexistingCompletedSetPreserved=true
retentionApply=NOT_RUN
offHostCopy=NOT_RUN
offHostDurabilityStatus=DEFERRED_ACCEPTED_RISK
currentIndependentOffHostTarget=NONE
```

Backup PASS 전에는 route 또는 HomeOps를 변경하지 않았다. Production restore, retention/delete와 off-host copy는 실행하지 않았으며 이 결과를 DR PASS로 표현하지 않는다.

## Cloudflare와 공개 transport

```text
publicHostname=forms.chochiho.cloud
cloudflareOrigin=http://form-dock-web:8080
dnsResolution=PASS
httpsCertificate=PASS
publicHealth=HTTP_200
unrelatedRouteCountBefore=7
unrelatedRouteCountAfter=7
unrelatedRouteSemanticPreservation=PASS
unexpectedRedirectCount=0
apiPublicDirectPortCount=0
postgresPublicPortCount=0
corsUntrustedOriginReflection=ABSENT
```

허용된 mutation은 exact FormDock hostname route/DNS 하나뿐이다. Existing tunnel identity, token, unrelated hostname/DNS와 account-wide 설정은 변경하지 않았다.

## Session과 Product canary

```text
publicCsrf=PASS
sameOriginLogin=PASS
sessionHttpOnly=PASS
sessionSecure=PASS
sessionSameSite=PASS
sessionFixationProtection=PASS
canarySurveyCount=1
canaryQuestionCount=1
canaryAnonymousResponseCount=1
publicSurveyRead=PASS
creatorResultsList=PASS
creatorResultsSummary=PASS
creatorResultsDetail=PASS
csvExport=PASS
canaryFinalLifecycle=CLOSED
manualDatabaseWriteOrDelete=0
```

Canary는 `[PROD-ACCEPTANCE]` title prefix, required `SHORT_TEXT` Question 하나와 anonymous Response 하나로 제한했다. Identifier, credential, answer payload와 CSV content는 evidence에 기록하지 않았다.

## HomeOps 통합

```text
monitoringAuthority=HomeOps
formDockMonitoredService=HEALTHY
formDockOpenIncidentCount=0
publicHealthCheckIntervalSeconds=60
publicHealthFailureThreshold=3
publicHealthRecoveryThreshold=2
formDockNotificationEligibility=false
homeOpsNotificationsEnabled=false
deploymentReporter=PASS_EXACT_EVENT_1
backupReporter=PASS_EXACT_EVENT_1
historicalReplayCount=0
pendingReporterSpoolCount=0
DISK_LOW=SUPPORTED_REPORTER_SIGNAL_TO_SIGNALS
HTTP_5XX_BURST=SUPPORTED_REPORTER_SIGNAL_TO_SIGNALS
```

기존 monitoring allowed origin 3개를 보존하고 exact FormDock origin 하나만 추가했다. HomeOps DB/Web는 그대로 유지하고 API만 accepted immutable image와 current runtime-config를 사용해 supported Compose path로 재생성했다. HomeOps signal capability의 `ALERT → RECOVERED` canary는 #108에서 이미 accepted됐으므로 false FormDock incident를 만들지 않고 mapping만 검증했다.

## Mutation 집계와 비작업 범위

```text
formDockImageMutationCount=0
formDockSchemaMigrationCount=0
formDockVolumeMutationCount=0
cloudflareTargetRouteMutationCount=1
cloudflareUnrelatedMutationCount=0
homeOpsAllowedOriginAppendCount=1
homeOpsApiRecreateCount=1
homeOpsDbOrWebRecreateCount=0
homeOpsServiceCreateCount=1
homeOpsDeploymentEventCount=1
homeOpsBackupEventCount=1
ghcrMutationCount=0
notificationMutationCount=0
secretValueExposureCount=0
phase6ProductMutationCount=0
```

Trusted Creator input은 삭제·회전·출력하지 않았다. Production image build/publish, manual SQL, Flyway 변경, backup restore, Cloudflare unrelated route, HomeOps source, Discord와 GHCR은 변경하지 않았다.

## 최종 경계

```text
PRODUCTION_ACTIVATION=ACTIVE_ACCEPTED
PUBLIC_ROUTE=ACTIVE_ACCEPTED
HOMEOPS_INTEGRATION=ACTIVE_ACCEPTED
OFF_HOST_DURABILITY=DEFERRED_ACCEPTED_RISK
PHASE_6=NOT_AUTHORIZED
```

Production acceptance는 Phase 6 Dogfooding authorization이 아니다. Independent off-host durability와 Creator credential 회전은 별도 operations decision으로 남긴다.
