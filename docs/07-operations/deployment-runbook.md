---
title: Deployment Runbook
status: draft
version: 0.9
last_updated: 2026-08-29
---

# 1. Target

Mac mini.

# 2. Release Inputs

- repository Release tag와 exact main SHA
- exact Git commit SHA
- API image digest
- Web image digest
- runtime compose/config revision
- candidate deployment state와 exact previous state identity 또는 first-activation `NONE`

Phase 4 baseline은 `v0.4.0` / `main@1648047645720e67d5e928345c875dc53a93ff0e`이다. 이 repository identity는 publish된 runtime image 또는 Production deployment evidence가 아니다.

# 3. Preflight

- current health
- disk
- Docker
- backup readiness
- DB compatibility
- operation lock

Database는 `fresh Production DB` 또는 `existing live Production DB/data`로 exact evidence를 남긴다. Phase 5-D1은 initial target을 `FIRST_ACTIVATION / FRESH_PRODUCTION_DB`로 분류했고 D2A가 exact digest runtime과 Flyway V1→V6를 활성화했다. D2B는 fresh pre-public backup 뒤 public/HomeOps acceptance를 완료했다. 이후 operation은 existing live Production DB/data로 취급하며 확인 없이 migration/backup 필요 여부를 추정하지 않는다.

# 4. Deploy

권장 흐름:

```text
Validate
→ Build/Publish
→ Predeploy backup
→ Pull
→ Stage
→ Activate
→ Health
→ Public smoke
→ Commit deployment state
```

Repository `dev → main` Release와 Production deploy는 같은 승인 단계가 아니다. `main` 승격만으로 live migration, Secret 변경, Cloudflare 변경 또는 Production activation을 자동 승인하지 않는다.

Recurring CD의 canonical workflow는 `.github/workflows/publish-and-deploy.yml`이며 [ADR-0007](../08-decisions/adr-0007-production-cd-change-gate.md)을 따른다. `main` push 또는 current-main-only dispatch 뒤 Validate와 latest successful Production baseline 이후 누적 diff를 분류한다. `MAC_MINI_DEPLOY_ENABLED`가 정확히 `true`이고 분류가 `APPLICATION_ONLY`인 경우에만 native ARM64 publish 및 protected `Production` environment 단계로 진입한다. Baseline 부재/모호함, docs-only, deploy-control, migration/data와 unknown은 publish/deploy하지 않는다.

Production canonical Compose는 `infra/compose.production.yaml`이고 API/Web의 exact image input과 private configuration env file을 요구한다. `infra/production.env.example`은 key interface만 제공하며 실제 credential source가 아니다. `infra/compose.yaml` local baseline을 Production deploy에 사용하지 않는다.

`infra/delivery/`은 state validation, isolated stage/health와 application rollback interface를 제공한다. Repository smoke는 local image ID와 disposable project만 사용하며 Pull, Activate, public smoke 또는 live state commit을 실행하지 않는다. 실제 activation command는 5-D2의 exact target/operation lock/rollback 승인 전에는 실행하지 않는다.

Recurring installed interface는 `infra/production/forced-command.sh.example`과 runtime-config artifact의 `deploy-release.sh`다. Forced command는 fixed FormDock project, current-main SHA, three exact digest, registry identity와 workflow run ID만 허용한다. Worker는 `/Users/homeserver/Server/apps/form-dock`, private `product.env`/`cd.env`/`deployment.state`, immutable runtime-config pointer와 existing HomeOps reporter를 사용한다. 설치, authorized key, Tailscale, GitHub Environment/Variable/Secret와 initial Production baseline은 별도 Ops Gate가 소유한다.

별도 activation Ops는 `MAC_MINI_DEPLOY_ENABLED`, protected `Production` environment와 environment-scoped `TS_OAUTH_CLIENT_ID`, `TS_AUDIENCE`, `HOME_MINI_SSH_KEY`, `HOME_MINI_KNOWN_HOSTS`의 존재·범위·approval 대기를 값 노출 없이 read-back해야 한다. 이름이 문서화됐다는 사실은 설정 완료나 Production mutation 승인이 아니다.

# 5. Failure

activation/health/public smoke 실패 시 이전 image/config rollback 경로를 유지한다.

Recurring worker는 accepted application/runtime-config로 rollback하고 PostgreSQL container/volume을 그대로 검증한다. Failed candidate나 rollback은 successful GitHub Production baseline과 current state를 전진시키지 않는다. Pending runtime-config pointer는 explicit recovery evidence로 남기며 destructive cleanup을 자동 실행하지 않는다.

# 6. Database

새 Flyway migration이 있으면 backward compatibility를 별도 검토한다.

이미 적용된 migration을 되돌리기 위해 기존 migration 파일을 수정하거나 자동 destructive down migration을 수행하지 않는다. Release rollback은 previous application과 forward schema compatibility를 별도로 확인한다.

# 7. Manual Deploy

초기 V1에서는 manual deployment를 허용할 수 있으나 exact SHA/digest를 기록한다.

# 7.1 Phase 5 serial ownership

```text
5-A Production Runtime Foundation
→ 5-B Backup/Restore/Recovery Readiness
→ 5-C1 Delivery/Monitoring Foundation
→ 5-C2 Exact Remote Artifact Publication Evidence
→ 5-D1 Production Activation Preflight
→ 5-D2A Local Production Bootstrap
→ 5-D2B Public/HomeOps Final Activation
```

- 5-A는 repository Production Compose/config와 isolated validation만 수행한다.
- 5-B는 logical backup tooling과 disposable scratch restore evidence를 소유한다.
- 5-C1은 deployment state, canonical Compose isolated stage/health/application rollback, bounded log와 provider-neutral monitoring contract를 소유한다.
- 5-C2는 5-C1 merge 뒤 별도 Issue가 승인한 exact remote artifact ref의 publication evidence만 소유한다.
- 5-D1은 target/artifact/database/config/lock/backup/routing/monitoring을 read-only로 분류한다.
- 5-D2A는 별도 명시 승인 아래 local Secret/config, fresh DB, deploy, local acceptance와 첫 backup/scratch restore를 완료했다.
- 5-D2B는 Issue #95의 explicit Production Operations Gate 아래 Cloudflare/HomeOps/public routing과 public smoke를 완료했다.

앞 slice의 PASS는 다음 slice 또는 live operation을 자동 승인하지 않는다.

5-A validation은 local-only temporary image와 disposable Compose project를 사용한다. 그 결과는 network/exposure/health/persistence contract evidence지만 published artifact, live data, target host 또는 public route acceptance가 아니다.

5-B tooling authority는 [`infra/backup/`](../../infra/backup/README.md)이다. Repository smoke는 disposable source/scratch만 사용하고 completed artifact, checksum/metadata, bounded retention, filesystem copy, Flyway V1→V6/data와 restored API health를 검증한다. 이 결과는 actual Production backup, off-host independence, live recovery 또는 schedule activation evidence가 아니다.

5-C1 tooling authority는 [`infra/delivery/`](../../infra/delivery/README.md)과 [`infra/monitoring/`](../../infra/monitoring/README.md)이다. Candidate/previous state linkage, application rollback과 monitoring event는 local/disposable evidence다. GHCR login/push, package mutation, Production env/project/DB와 notification provider를 사용하지 않는다.

5-C2 published artifact identity는 [Phase 5-C2 Remote Artifact Publication Evidence](../06-quality/phase-5-c2-remote-artifact-publication-evidence.md)에 고정한다. Issue #89의 exact GitHub-hosted job만 package write를 사용했고 이후 verification은 recorded digest를 read-only 비교한다. Operator는 tag가 아닌 evidence의 digest ref를 후속 input authority로 사용한다. 이 artifact를 Mac mini에 pull하거나 Production Compose에 적용하는 작업은 Phase 5-D2 exact environment authorization 전까지 금지한다.

Application rollback command는 exact previous state를 same project에 적용하고 DB volume을 보존한다. Database recovery는 Phase 5-B/5-D2 절차이며 application rollback에 `down --volumes`, Flyway file 변경 또는 destructive down migration을 결합하지 않는다.

Phase 5-D2A live action을 별도 승인받기 전에는 `backup.sh`, `retention.sh`, `copy-off-host.sh` 또는 restore tooling을 Production environment에 실행하지 않는다. Exact source/target, private credential mechanism, current database classification, disk, operation lock, required backup와 rollback을 먼저 확인한다. Issue #93은 first local activation 뒤 첫 `backup.sh`/`verify.sh`와 disposable `restore-scratch.sh`까지만 승인하며 retention apply/schedule과 off-host copy는 포함하지 않는다.

Issue #93의 canonical D2A helper는 [`infra/production/activate-first.sh`](../../infra/production/activate-first.sh)이다. Trusted bootstrap input과 actual D1 preflight를 모든 mutation보다 먼저 검사하고 exact `v0.4.0` digest, `form-dock`, loopback `18082`, existing `edge`만 허용한다. Bootstrap finalization은 API만 final credential-empty config로 재생성하며 PostgreSQL container/volume과 JDBC session을 보존한다.

# 7.2 Secret and configuration gate

Production credential 값은 repository, Issue, PR, command log와 evidence에 기록하지 않는다. D2 canonical mechanism은 repository 밖 owner-only directory, mode `600` env file과 explicit Compose `--env-file`이다. Private directory는 mode `700`, deployment state는 mode `600`을 적용한다. Configuration revision은 non-secret management identity만 기록하고 Secret bytes나 Secret-derived hash를 포함하지 않는다.

# 7.3 Operation lock와 first activation

D2는 repository 밖 private directory에서 atomic `mkdir`로 single operation lock을 획득한다. Existing lock은 fail closed하며 자동 삭제하지 않는다. Stale 후보는 owner process 부재, current/candidate state와 진행 중 container operation 부재를 operator가 확인한 뒤 explicit recovery로만 제거한다.

Current previous deployment state는 `NONE`이다. Candidate state는 exact release SHA/API-Web digest/Compose revision/non-secret configuration revision을 기록한다. Application rollback은 PostgreSQL volume을 보존하고 `down --volumes`나 destructive Flyway down migration을 실행하지 않는다.

# 7.4 Cloudflare와 HomeOps activation state

Current public route는 `forms.chochiho.cloud → containerized cloudflared → external edge → http://form-dock-web:8080`이며 Issue #95 D2B에서 active/accepted로 검증했다. Web만 `edge`에 참여하고 API/PostgreSQL은 참여하지 않으며 public host port도 없다.

Monitoring authority는 HomeOps다. D2B는 FormDock public HTTPS health service와 deployment/backup reporter를 구성하고 `DISK_LOW`/`HTTP_5XX_BURST` supported mapping을 확인했다. `HOMEOPS_NOTIFICATIONS_ENABLED=false`와 service notification eligibility `false`는 `DISABLED_BY_OPERATOR_CHOICE`로 유지한다. 상세 상태는 [Phase 5-D2B evidence](../06-quality/phase-5-d2b-public-homeops-activation-evidence.md)를 따른다.

# 8. Release Tag Policy

## 8.1 Tagging boundary

Release tag는 **검증된 `main` Release commit**에만 생성한다.

기본 흐름은 다음과 같다.

```text
feature / fix / docs / chore
→ PR → dev
→ integration validation
→ dev → main Release PR
→ Merge Commit
→ exact merged main 검증
→ Release tag
```

다음 위치에는 Release tag를 생성하지 않는다.

```text
feature branch commit
dev commit
일반 feature/fix/docs/chore PR head
아직 main에 승격되지 않은 Release Candidate
```

Tagging은 Feature 완료 표시가 아니라 Repository Release baseline을 식별하는 작업이다.

## 8.2 Post-merge verification before tag

Tag 생성 전에 최소 다음을 확인한다.

- tag 대상 SHA가 현재 `main` history에 존재한다.
- 해당 SHA가 의도한 `dev → main` Release merge commit 또는 별도 승인된 `main` corrective/hotfix Release commit이다.
- Release PR의 required checks와 release evidence가 PASS다.
- merged `main` tree가 검토한 release tree와 일치한다.
- unresolved release blocker가 없다.
- migration/recovery 영향이 Release evidence와 모순되지 않는다.

Production deployment가 별도 Gate인 경우 Production 미배포 상태에서도 Repository Release tag를 만들 수 있다. 단, tag 자체를 Production deployment/activation 성공 증거로 사용하지 않는다.

## 8.3 Tag form

기본적으로 annotated tag를 사용한다.

```bash
git tag -a vX.Y.Z <MAIN_RELEASE_SHA> -m "FormDock vX.Y.Z - <release name>"
git push origin vX.Y.Z
```

`git push --tags`로 unrelated local tag를 일괄 push하지 않고, 생성한 tag를 이름으로 명시해 push한다.

## 8.4 Tag immutability

Remote에 push된 Release tag는 immutable release identity로 취급한다.

금지:

```text
기존 tag를 다른 commit으로 이동
같은 version tag 재사용
force-update / force-push로 tag 교체
잘못된 commit을 숨기기 위한 tag history rewrite
```

이미 push된 tag가 잘못됐다면 tag를 이동시키지 않고 상황을 기록한 뒤 새 version으로 후속 Release를 만든다. 보안상 tag 제거 자체가 필요한 예외 상황은 별도 운영 결정으로 처리한다.

## 8.5 Corrective release before first accepted tag

`main` Release 이후 acceptance 과정에서 corrective fix가 필요하고 아직 해당 capability의 Release tag를 만들지 않았다면, 실패가 확인된 초기 `main` commit에 tag를 먼저 만들지 않는다.

```text
initial main Release
→ acceptance blocker 발견
→ corrective fix
→ corrective main Release
→ final acceptance
→ 최초 Release tag를 최종 accepted main commit에 생성
```

이미 tag가 존재하는 accepted Release 이후 corrective fix가 필요하다면 기존 tag를 이동하지 않고 PATCH version 후속 Release를 사용한다.

## 8.6 Pre-1.0 versioning

FormDock가 `1.0.0` 이전인 동안 기본 versioning은 다음처럼 운영한다.

- 의미 있는 새 vertical capability의 `main` Release: MINOR 증가 (`0.x.0`)
- 이미 tag된 Release의 호환 corrective release: PATCH 증가 (`0.x.y`)
- 아직 final Repository Release로 확정하지 않은 명시적 milestone이 필요할 때만 prerelease suffix를 사용한다 (`-alpha.N`, `-rc.N`).

Phase 번호와 version 번호를 영구적으로 1:1 결합하지 않는다. Version은 Release 의미와 compatibility를 기준으로 정한다.

## 8.7 Tag push is not Production authority

Release tag 생성 또는 push는 다음 권한을 부여하지 않는다.

```text
Production deploy
live Flyway migration
backup/restore 실행
Secret/credential 변경
Cloudflare/public URL 변경
Phase authorization 확대
live user data mutation
```

현재 GitHub Actions가 tag push를 trigger로 사용하지 않더라도, 향후 workflow 변경 시 이 정책을 다시 검토한다. Tag-triggered 자동화가 추가되면 Release/Production Gate와 충돌하지 않도록 별도 승인 경계를 정의해야 한다.

## 8.8 GitHub Release

GitHub Release publication은 tag 생성과 별도 단계다.

```text
verified main Release commit
→ annotated tag
→ 필요 시 Release Notes 검토
→ GitHub Release publish
```

GitHub Release를 자동 생성하는 workflow는 별도 승인 없이 추가하지 않는다.
