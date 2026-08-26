---
title: Deployment Runbook
status: draft
version: 0.3
last_updated: 2026-08-26
---

# 1. Target

Mac mini.

# 2. Release Inputs

- repository Release tag와 exact main SHA
- exact Git commit SHA
- API image digest
- Web image digest
- runtime compose/config revision

Phase 4 baseline은 `v0.4.0` / `main@1648047645720e67d5e928345c875dc53a93ff0e`이다. 이 repository identity는 publish된 runtime image 또는 Production deployment evidence가 아니다.

# 3. Preflight

- current health
- disk
- Docker
- backup readiness
- DB compatibility
- operation lock

Database는 `fresh Production DB` 또는 `existing live Production DB/data`로 exact evidence를 남긴다. 확인 전에는 migration/backup 필요 여부를 추정하지 않는다.

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

# 5. Failure

activation/health/public smoke 실패 시 이전 image/config rollback 경로를 유지한다.

# 6. Database

새 Flyway migration이 있으면 backward compatibility를 별도 검토한다.

이미 적용된 migration을 되돌리기 위해 기존 migration 파일을 수정하거나 자동 destructive down migration을 수행하지 않는다. Release rollback은 previous application과 forward schema compatibility를 별도로 확인한다.

# 7. Manual Deploy

초기 V1에서는 manual deployment를 허용할 수 있으나 exact SHA/digest를 기록한다.

# 7.1 Phase 5 serial ownership

```text
5-A Production Runtime Foundation
→ 5-B Backup/Restore/Recovery Readiness
→ 5-C Delivery/Monitoring Readiness
→ 5-D Production Activation Gate
```

- 5-A는 repository Production Compose/config와 isolated validation만 수행한다.
- 5-B는 logical backup tooling과 disposable scratch restore evidence를 소유한다.
- 5-C는 exact artifact publication/deployment 및 monitoring contract를 소유한다. Remote publish는 exact ref를 승인한 Issue가 필요하다.
- 5-D만 별도 명시 승인 아래 live Secret/config, DB action, deploy, Cloudflare/public routing과 public smoke를 수행할 수 있다.

앞 slice의 PASS는 다음 slice 또는 live operation을 자동 승인하지 않는다.

# 7.2 Secret and configuration gate

Production credential 값은 repository, Issue, PR, command log와 evidence에 기록하지 않는다. Secret storage/injection/rotation mechanism은 activation 전 별도 operations/security contract에서 결정하며, 이번 Entry는 특정 mechanism을 선택하지 않는다.

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
