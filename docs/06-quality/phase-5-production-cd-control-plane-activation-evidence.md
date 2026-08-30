# Phase 5 Production CD Control-plane Activation Evidence

## 판정

```text
CD Foundation Main Release          RELEASED
Production CD Control Plane         ACTIVE + ACCEPTED
Production CD Automation            ARMED
Current Application                 v0.4.0 PRESERVED
First Automatic Application Deploy  PENDING
Production Notification             DISABLED_BY_OPERATOR_CHOICE
Off-host Durability                 DEFERRED_ACCEPTED_RISK
Phase 6 Dogfooding                   NOT AUTHORIZED
```

Issue #103의 두 개 직렬 Gate를 따랐다. Gate A는 repository-side recurring CD foundation을 `main`에 release했고, Gate B는 별도 Production Operations authority로 GitHub/host control plane을 구성했다. Current v0.4.0 API/Web, PostgreSQL volume/Flyway, Cloudflare route, HomeOps notification은 변경하지 않았다.

## 고정 근거 식별자

```text
issue=103
activationSourceMainSha=2e149318dc62d9469e03d82f85adff38fa28eb8a
activationSourceMainTree=05110dc16c5b80b8de25fb43e678c00dfbaed97a
releaseBackSyncMergeSha=4b13f692c2da79ca83f6666c7b793acdf1b0f0ca
acceptedApplicationRelease=v0.4.0
acceptedApplicationSha=1648047645720e67d5e928345c875dc53a93ff0e
apiArtifact=ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b
webArtifact=ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551
targetPlatform=linux/arm64
initialProductionDeployment=6161908082
safeNoOpWorkflowRun=33285294463
finalRepositoryIntegrationAuthority=GITHUB_PR_REQUIRED_CHECKS_AND_MERGED_DEV
```

Activation source와 safe no-op run은 완료된 historical evidence다. 이 문서는 PR의 latest Hosted run ID나 Ready 순간 상태를 canonical field로 고정하지 않는다. Repository integration의 최종 authority는 GitHub PR required checks와 merged `dev` exact SHA/tree다.

## Gate A — Repository release

```text
cdFoundationMainRelease=PASS
mainReleaseTree=EXACT_REVIEWED_DEV_TREE
publishAndDeployWorkflowGraph=PASS
selfActivation=BLOCKED_BY_CHANGE_GATE
unexpectedGhcrPublicationCount=0
unexpectedMacMiniDeploymentCount=0
```

Repository foundation release는 `main` event를 orchestration trigger로 설치했지만 mutation authority로 사용하지 않았다. Startup permission 문제를 corrective release로 해소한 뒤 exact `main` workflow가 Validate와 fail-closed change gate를 정상 생성했다.

## Gate B — GitHub control plane

```text
productionEnvironment=ACTIVE
productionBranchPolicy=MAIN_ONLY
requiredReviewerCount=0
soleOperatorDecision=ACCEPTED
environmentScopedSecretNameCount=4
repositoryWideDuplicateSecretCount=0
restrictedSsh=INSTALLED_ACCEPTED
macMiniDeployEnabled=true
```

Environment-scoped deploy input은 `TS_OAUTH_CLIENT_ID`, `TS_AUDIENCE`, `HOME_MINI_SSH_KEY`, `HOME_MINI_KNOWN_HOSTS`다. 값은 repository, Issue, PR, log와 이 evidence에 기록하지 않았다. Production branch policy는 exact `main`만 허용하며, sole-operator 결정에 따라 required reviewer와 prevent-self-review를 요구하지 않는다.

## Stable runtime과 initial baseline

```text
runtimeAuthority=BOOTSTRAP_LOCAL_RUNTIME_GENERATION
runtimeState=ACTIVE_ACCEPTED
runtimeCurrentPointer=PASS
privateStateOwnerMode=PASS
operationLock=ABSENT
postgresVolume=UNCHANGED
flyway=V1_TO_V6_FAILURE_0
latestLogicalBackup=VERIFIED
initialProductionBaseline=SUCCESS
baselineResolver=EXACT_CURRENT_MAIN
```

Exact `main` source의 deterministic local runtime generation은 OCI/GHCR artifact로 표현하지 않는다. 첫 eligible application deploy의 runtime-config OCI artifact가 이 local bootstrap authority를 대체해야 한다. Initial GitHub Production Deployment는 current application v0.4.0, runtime authority, Flyway와 health를 non-secret metadata로 기록한 successful baseline이다.

## Kill switch와 safe no-op

```text
macMiniDeployEnabledReadBack=true
safeNoOpEvent=workflow_dispatch
safeNoOpHead=EXACT_CURRENT_MAIN
safeNoOpBaseline=EXACT_CURRENT_MAIN
safeNoOpChangedFileCount=0
safeNoOpClassification=DOCS_META_ONLY
safeNoOpHoldReason=NO_APPLICATION_CHANGE
safeNoOpBackend=SUCCESS
safeNoOpFrontend=SUCCESS
safeNoOpInfrastructure=SUCCESS
safeNoOpArtifactPublication=SKIPPED
safeNoOpProductionDeployment=SKIPPED
safeNoOpResult=SUCCESS
```

Safe no-op는 automation control plane이 무해하게 기동되면서 baseline과 같은 main을 배포하지 않는지를 검증한다. Artifact publication과 Mac mini candidate transaction은 실행되지 않았으므로 첫 eligible `APPLICATION_ONLY` release의 GitHub-hosted OIDC, restricted SSH와 end-to-end deployment는 아직 미검증이다.

## Application과 운영 불변

```text
postgresApiWebHealth=PASS
containerRestartCount=0
apiWebArtifactIdentity=EXACT_V0_4_0_DIGESTS
apiPublicHostPortCount=0
postgresPublicHostPortCount=0
webLoopbackBind=127.0.0.1:18082
publicHealth=HTTPS_200_REDIRECT_0
productDataMutationCount=0
schemaMigrationCount=0
cloudflareMutationCount=0
homeOpsMutationCount=0
notificationMutationCount=0
ghcrMutationCount=0
secretValueExposureCount=0
```

Fresh logical backup 한 세트는 append-only로 생성하고 checksum, metadata와 custom-format readability를 검증했다. Retention/off-host copy/restore는 수행하지 않았으며 independent off-host durability는 `DEFERRED_ACCEPTED_RISK`다. HomeOps notification은 `false`, service notification eligibility는 disabled 상태를 유지했다.

## Rollback과 후속 Gate

```text
controlPlaneRollback=SET_MAC_MINI_DEPLOY_ENABLED_FALSE
applicationRollback=NOT_REQUIRED_FOR_ACTIVATION
databaseRollback=NOT_APPLICABLE
firstEligibleApplicationDeploy=PENDING
phase6Dogfooding=NOT_AUTHORIZED
```

Unexpected workflow behavior나 control-plane drift가 발생하면 우선 `MAC_MINI_DEPLOY_ENABLED=false`로 전환해 새 publication/deploy candidate를 차단한다. 이 activation은 application이나 database를 전진시키지 않았으므로 rollback을 요구하지 않았다.

## 최종 경계

```text
CD_FOUNDATION_MAIN_RELEASE=RELEASED
CD_CONTROL_PLANE=ACTIVE_ACCEPTED
AUTO_DEPLOY=ARMED
CURRENT_APPLICATION=V0_4_0_PRESERVED
FIRST_APPLICATION_AUTO_DEPLOY=PENDING
PRODUCTION_NOTIFICATIONS=DISABLED_BY_OPERATOR_CHOICE
OFF_HOST_DURABILITY=DEFERRED_ACCEPTED_RISK
PHASE_6=NOT_AUTHORIZED
```

`ARMED`는 후속 application release가 무조건 배포된다는 뜻이 아니다. Cumulative diff가 `APPLICATION_ONLY`이고 artifact, Environment, host preflight와 transaction gate가 모두 통과한 candidate만 배포 대상이다. Migration/data, deploy-control, unknown과 docs-only 변경은 계속 fail closed로 publish/deploy하지 않는다.
