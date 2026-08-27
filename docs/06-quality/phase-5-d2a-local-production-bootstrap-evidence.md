# Phase 5-D2A Local Production Bootstrap Evidence

## 판정

```text
Phase 5-D1 Activation Preflight  COMPLETE + DEV INTEGRATED
Phase 5-D2A Local Bootstrap      LOCAL ACTIVE + ACCEPTED — DEV INTEGRATION PENDING
Phase 5-D2B Public/HomeOps       NOT AUTHORIZED
Public Route                     ABSENT — DNS NXDOMAIN
HomeOps FormDock Configuration   NOT MUTATED
Production Activation           INCOMPLETE — D2B REQUIRED
```

Issue #93의 D2A live-operation authorization만 사용해 exact `v0.4.0` artifact를 Mac mini의 local Production runtime으로 처음 활성화했다. Fresh PostgreSQL에 Flyway V1→V6을 적용하고 Creator bootstrap/finalization, loopback same-origin acceptance, 첫 logical backup 검증과 scratch restore를 완료했다. Cloudflare route, HomeOps configuration, GHCR, public endpoint와 Product data fixture는 변경하지 않았다.

## Machine-readable identity

```text
operationUtc=2026-08-27T05:18:42Z
issue=93
repositoryBaseSha=a2ddc28a6af42d085b804eef2bfac4123ad3ef5b
repositoryBaseTree=6c2fd95c20b3d2a0e7e492efea068b269862fb89
activationHelperChangesetSha256=c3129eb9a23e01993268f51a7ea9d71e5904e86b20c072f60e3c873f0a770d9a
repositoryIntegrationCommit=4035f20cac0c02a371a4d05d3181042576cc7e43
repositoryIntegrationTree=b9c2b9bf8005a8e20fe757e28a9babafb9a5a4e7
repositoryIntegrationStatus=PR_READY_DEV_INTEGRATION_PENDING
pullRequest=94
publicationHostedValidationRun=33044722086
publicationHostedBackend=PASS
publicationHostedFrontend=PASS
publicationHostedInfrastructure=PASS
publicationHostedArm64=SKIPPED_EXPECTED
publicationHostedGhcrEvidence=SKIPPED_EXPECTED
finalMergeGateAuthority=GITHUB_PR_REQUIRED_CHECKS
requiredChecks=Backend,Frontend,Infrastructure
canonicalProductionComposeSha256=1156d5bea09e404c4c5f01a62e85b6cf956d4db4561cbd12be4d5541fceae673
releaseTag=v0.4.0
releaseGitSha=1648047645720e67d5e928345c875dc53a93ff0e
releaseTree=630ea7b38a721874a447022493be7e49d9d42905
apiArtifact=ghcr.io/xxh3898/form-dock-api@sha256:49c98b1964ba3951569c75f941507337f1a1172bcff7a8af3e694b2dc9675c8b
webArtifact=ghcr.io/xxh3898/form-dock-web@sha256:19bde4d64e608f0b5e4ed5fefe96947dbdc8830dc4f3f5837290384a32f63551
targetPlatform=linux/arm64
```

실행 helper는 위 `repositoryBaseSha`의 Issue #93 working tree에서 실행했다. 실행에 사용한 `common.sh`, `preflight.sh`, `activate-first.sh` blob manifest는 `activationHelperChangesetSha256`으로 고정했다. Helper와 evidence의 최초 repository publication은 위 exact commit/tree와 PR #94이며, 당시 Hosted Validate run `33044722086`의 Backend, Frontend, Infrastructure가 통과했다. 최종 merge gate는 이 문서에 특정 latest run을 자기참조로 기록하지 않고 GitHub PR의 현재 required checks를 authority로 사용한다. Tag가 아니라 위 immutable digest refs가 runtime artifact authority다.

## Runtime과 network

```text
composeProject=form-dock
activationClass=FIRST_ACTIVATION
databaseClass=FRESH_PRODUCTION_DB
previousState=NONE
containerHealth=POSTGRES_API_WEB_HEALTHY
webBind=127.0.0.1:18082
apiHostPortCount=0
postgresHostPortCount=0
webEdgeAlias=form-dock-web
apiEdgeAttachment=ABSENT
postgresEdgeAttachment=ABSENT
webHealth=PASS
apiHealth=UP
databaseHealth=UP
```

Web만 loopback port와 existing external `edge` network에 연결했다. API는 application/database network, PostgreSQL은 internal database network에만 참여한다. Mac mini에서 application image를 build하거나 moving tag를 사용하지 않았다.

## Schema, Creator와 session

```text
flywayHistory=1,2,3,4,5,6
flywaySuccessfulCount=6
flywayFailedCount=0
creatorCount=1
adminCreatorCount=1
creatorBootstrap=PASS
bootstrapFinalRuntime=DISABLED_EMPTY
finalLoginSessionSafeRead=PASS
jdbcSessionPersistence=PASS
postgresContainerPreserved=PASS
postgresVolumePreserved=PASS
```

Trusted bootstrap input은 repository 밖 owner-only mode `600` 파일로 전달했으며 identity, password와 password hash를 evidence에 기록하지 않았다. API bootstrap container를 final configuration으로 재생성한 뒤 `FORMDOCK_BOOTSTRAP_ENABLED=false`와 empty email/password/display name을 확인했다. PostgreSQL container/volume을 유지한 상태에서 기존 JDBC session의 `/api/auth/me`, 새 login과 empty `/api/surveys` safe read를 모두 검증했다.

User가 요청한 임시 Creator credential은 operator-managed input에 mode `600`으로 남아 있으며 별도 명시 승인 아래 회전·제거해야 한다. 이는 final API runtime env에 bootstrap Secret이 남아 있다는 뜻이 아니며, 현재 Creator credential 자체는 회전 전까지 유효하다.

## Backup와 recovery

```text
predeployBackup=NOT_REQUIRED_FRESH_DB
firstLocalLogicalBackup=COMPLETE
backupVerification=PASS
scratchPostgresHealth=PASS
scratchFlywayHistory=1,2,3,4,5,6
scratchRepresentativeData=PASS
scratchApiHealth=PASS
scratchResidue=0
retentionApply=NOT_RUN
offHostCopy=NOT_RUN
offHostDurabilityStatus=DEFERRED_ACCEPTED_RISK
currentIndependentOffHostTarget=NONE
```

첫 completed backup set의 metadata, checksum과 dump integrity를 검증하고 disposable scratch PostgreSQL/API에서 restore했다. Production DB restore, retention apply/schedule과 off-host copy는 수행하지 않았다. Independent off-host target 부재는 D1에서 승인된 risk로 남으며 durability 또는 disaster-recovery PASS로 표현하지 않는다.

## Operation safety와 negative scope

```text
actualRepreflight=PASS
operationLock=ACQUIRED_THEN_RELEASED
privateDirectoryMode=700
privateEnvStateEvidenceMode=600
deploymentState=ACCEPTED
publicDns=NXDOMAIN
cloudflareMutationCount=0
homeOpsMutationCount=0
ghcrMutationCount=0
productFixtureWriteCount=0
secretLeakFindingCount=0
```

Mutation 전 actual re-preflight가 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`와 resource conflict 0을 다시 확인했다. Accepted state는 runtime, local acceptance와 recovery evidence가 모두 통과한 뒤에만 확정했고 operation lock은 정상 해제됐다. Cloudflared와 existing HomeOps Web/API/DB는 read-only health 확인 뒤에도 unchanged/healthy였다.

## Gate boundary

D2A local runtime은 active하고 위 범위에서 accepted다. 이 evidence와 helper changeset은 아직 `dev`에 통합되지 않았으므로 repository authorization state는 `DEV INTEGRATION PENDING`이다. D2A PASS는 public readiness 또는 Production Activation completion이 아니다.

남은 repository integration lifecycle은 다음 순서로 진행한다.

- user manual merge
- exact merged `dev` SHA/tree 검증
- exact post-merge `dev` Validate 검증
- Issue #93 completion evidence 확인과 completed close
- 이후 별도 authorization에 따른 Phase 5-D2B 검토

다음 운영 작업은 별도 authorization이 필요하다.

- 임시 Creator credential 회전·operator input 제거
- independent off-host durability hardening
- D2B Cloudflare route/DNS, exact public smoke와 HomeOps FormDock configuration

GitHub Release, `main` 변경, public route, Production data restore와 destructive volume operation은 수행하지 않았다.
