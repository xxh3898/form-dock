# PROJECT-OPS: FormDock

Related Issue: [#113](https://github.com/xxh3898/form-dock/issues/113)

이 문서는 [.ops/project.yaml](.ops/project.yaml)의 사람용 설명과 evidence index다. Machine contract는 profile 하나이며 중앙 규칙, 기존 Product 계약, 실행 승인을 재정의하지 않는다. 2026-10-14 KST의 G0 로컬 문서 구현 범위이며 commit/push/PR 및 운영 변경은 미승인이다.

## Authority

중앙 authority는 released [Platform Golden Path v1.2.1](https://github.com/xxh3898/platform-golden-path/tree/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c)이다. Standard/schema/minimum/revision의 machine 값은 profile의 `standard`가 소유한다. Schema/minimum은 `1.2.0-rc.1`, Gate Registry는 `1.0.0`이다. 중앙 default `dev`의 미출시 변경이나 문서의 STABLE 표기를 Release 증거로 재사용하지 않는다. [Exact release acceptance](https://github.com/xxh3898/platform-golden-path/actions/runs/38488420603)는 중앙 commit `851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c`의 push/main SUCCESS 기록이다.

- 운영 effective contract: [Authority Model](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/00-platform/authority-model.md)의 Central → Tier → profile → active approved exception → runbook 순서를 따른다.
- 규칙 소유자: [Authority Map](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/00-platform/authority-map.md). 이 문서는 운영 control의 새 owner가 아니다.
- Product/Domain/architecture의 기존 Source of Truth는 [AGENTS.md](AGENTS.md), [V1 Scope](docs/00-product/v1-scope.md), [accepted ADR](docs/08-decisions/README.md)의 범위와 순서를 유지한다. 기존 운영 구현·ADR이 중앙 운영 요구와 다른 부분은 gap이지 중앙 규칙의 override가 아니다. 변경은 별도 Issue/승인으로 해결한다.
- Adoption은 [Drift Management](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/00-platform/drift-management.md)와 [Versioning](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/00-platform/versioning.md)을 따른다. 현재 `UNASSESSED / report`; `PLANNED`, `MIGRATING`, `COMPLIANT`로 전환하지 않았다.

## Classification

Issue #113의 2026-10-14 최신 Owner 승인으로 Tier=targetTier=3, controlPlane=false를 확정했다. PostgreSQL에 Creator/Survey/Question/Response/Answer/JDBC session을 보존하며 사용자 원문과 질문으로 수집하는 개인정보가 포함될 수 있다. 실제 제출 원문은 source로 재생성할 수 없다. File upload는 V1 범위 밖이며 가짜 upload store를 추가하지 않는다.

FormDock의 자체 CD는 자기 workload만 대상으로 한다. 다른 프로젝트를 제어한다는 근거가 없으므로 명칭에 있는 “CD control plane”만으로 중앙 control-plane overlay를 적용하지 않는다. HomeOps·Cloudflare는 외부 운영 dependency이며, overlay 비적용은 그 dependency 부재나 복구 가능성을 증명하지 않는다. Optional 1.2 dependency/resource/public-identity/host-automation/schedule block은 이번 intake에서 생략했다. 생략은 operational N/A 또는 capability PASS가 아니며 exact pin·inventory는 후속 gap 평가 대상이다.

`project.lifecycle=LEGACY`는 기존 Production의 계약 도입 방식이다. Runtime이 종료됐다는 의미가 아니다. 분류 근거는 [Survey model](docs/01-domain/survey-model.md), [Response model](docs/01-domain/response-model.md), [Retention](docs/04-data/retention.md)이다.

## Owners and approval

`role:form-dock-owner`는 Issue #113을 승인한 repository Owner 역할이며 개인 연락처나 credential을 저장하지 않는다. 현재 profile의 repository/release/deployment/data/security/incident 책임은 이 역할로 연결했다. 역할이 같아도 승인 권한은 별개이며 대체·escalation 담당은 아직 정하지 않았다.

로컬 문서 구현만 승인됐다. Commit, push, PR, merge, Release, GitHub settings, Production, migration/data, Secret, backup/restore, Cloudflare/HomeOps/GHCR는 각각 별도 승인 대상이다. Gap 해결일과 운영 parameter 결정은 `UNKNOWN`이다. Profile 작성만으로 새 운영 권한을 만들지 않는다.

## Profile interpretation

Profile에는 실제 source binding과 중앙/Tier의 desired policy projection이 함께 있다. 다음 값을 installed capability나 현재 성공 기록으로 해석하지 않는다.

- `ci.secretScanRequired`, `ci.contractValidationRequired`, `release.artifact.*Required`, `deployment`의 transaction/lock/idempotency/acceptance 상수, `database.migration.authorityMode`, `backup.enabled` 및 검증/보존/DR/monitoring 요구: effective desired contract다. 실제 차이는 `adoptionGaps`와 아래 evidence에 남긴다. `backup.enabled=true`는 scheduler 활성화 관측이 아니다.
- Backup RPO/offsite RPO 6h, RTO 4h, 최대 6h 간격, verified local 3개와 daily14/weekly8/monthly6은 [Tier 3 기본 목표](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/00-platform/project-tiers.md)다. 실제 충족·측정은 UNKNOWN이다.
- Artifact 5세대는 released Tier 3 fixture의 desired 권고이며 현재 registry 보존을 증명하지 않는다. Credential lifetime 15분은 released template의 desired 상한 예시이며 현재 TTL이나 별도 운영 적용 승인이 아니다.
- Approval TTL, health timeout, observation duration, durable schema/operation directory, offsite target/alternate destination은 UNKNOWN이다. Source에서 확인된 state/lock/pending 이름은 operator가 검증할 logical binding이며 private absolute path를 공개하지 않는다. UNKNOWN은 생성할 path가 아니다.
- Profile의 `projectProcedureRef`나 retrieval reference가 이 문서의 gap anchor를 가리키면 **미구현/미검증 절차의 설명**이다. 실행 가능한 runbook을 새로 만들었다고 주장하지 않는다.
- `project.review`는 Owner의 최종 profile review 전이므로 null이다. `targetResolutionOn: UNKNOWN`은 날짜 미승인 상태이며 기한을 임의로 만들지 않는다. 모든 gap의 Issue #113 링크는 intake 기록이지 그 Issue가 remediation 실행까지 승인한다는 뜻이 아니다.

## Implementation map

기준 subject는 merged `dev@6622479a22e241a7a1392c04886a65c065d307b1`이다. Source의 존재는 current Production Gate PASS가 아니다.

| 영역 | 실제 source / evidence | 경계 |
| --- | --- | --- |
| CI | [Validate](.github/workflows/validate.yml) | Backend/Frontend/Infrastructure; 중앙 profile 검사와 전용 Secret/PII scan 편입은 후속 |
| Release/CD | [Publish and Deploy](.github/workflows/publish-and-deploy.yml), [ADR-0007](docs/08-decisions/adr-0007-production-cd-change-gate.md) | main-only, cumulative diff, exact source/digest, application-only guard 유지 |
| 추가 artifact evidence | [GHCR evidence workflow](.github/workflows/ghcr-publication-evidence.yml) | 별도 publication evidence; 실행·registry 변경 없음 |
| Runtime | [Production Compose](infra/compose.production.yaml) | ARM64 API/Web 및 PostgreSQL 18, private DB network, loopback Web; PostgreSQL image는 외부 base image이지 FormDock build artifact가 아님 |
| Runtime-config artifact | [Dockerfile](runtime-config.Dockerfile) | Compose와 worker/verification scripts를 same-SHA OCI artifact로 제공 |
| Deploy/lock/state | [Production tooling](infra/production/README.md), [worker](infra/production/deploy-release.sh), [wrapper](infra/production/forced-command.sh.example) | 설치된 host revision과 state는 UNKNOWN |
| Migration | [Flyway migrations](backend/src/main/resources/db/migration), [application config](backend/src/main/resources/application.yml) | Startup Flyway 활성 source; one-shot 분리 gap |
| Backup/restore | [Tooling](infra/backup/README.md), [recovery contract](docs/07-operations/backup-recovery.md) | pg_dump·checksum/readability·scratch restore 구현; 현재 snapshot/운영 schedule UNKNOWN |
| Monitoring | [Signals](infra/monitoring/README.md), [contract](docs/07-operations/monitoring.md), [HomeOps reporter](infra/production/report-homeops-deployment.sh) | 신호 구현과 실제 incident/notification delivery 분리 |
| Credentials | [Deployment contract](docs/07-operations/deployment-runbook.md), [CD workflow](.github/workflows/publish-and-deploy.yml) | Profile은 source에 문서화된 non-secret reference name만 사용; Secret 존재·값·권한을 새로 조회하지 않음 |

## Gate mapping

중앙 이름·의미는 [Gate Registry](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/00-platform/gate-registry.md)가 소유한다. G0~G8은 모두 `required`다. 다음은 대응 범위를 설명하며 기존 [FormDock Gate 0~5](docs/06-quality/quality-gates.md) 번호·의미를 바꾸지 않는다.

| 기존 FormDock Gate | 중앙 대응 범위 |
| --- | --- |
| Gate 0 Contract | G0 |
| Gate 1 PR Validation | G1 / G2 |
| Gate 2 dev Integration | G3 |
| Gate 3 main Release Candidate | G4와 artifact build eligibility; G5 publication 성공을 대신하지 않음 |
| Gate 4 Production Readiness/Activation | G6 / G7 / G8 |
| Gate 5 Dogfooding | Product Phase 6 acceptance; 중앙 G8로 대체하지 않음 |

Phase 0는 계약/scaffold, Phase 1~4는 Product vertical capability와 각각의 local Gate 1~3, Phase 5는 운영 foundation/activation, Phase 6는 dogfooding 범위다. Phase 번호를 중앙 Gate 번호로 변환하지 않는다. Phase 6-A launch는 역사적으로 완료됐고 #112 integration은 별도 repository evidence다. Phase 6-B NOT_AUTHORIZED, FormDock V1 INCOMPLETE를 유지한다.

이번 #113의 G0는 로컬 intake이며 UNASSESSED/open gaps 때문에 운영 G0 PASS가 아니다. G1은 로컬 shape/문서 검증 범위로만 보고한다. 신규 G2/G3는 commit/PR 전이므로 NOT RUN; G4/G5는 미실행; G6~G8은 current evidence 부족과 source gap 때문에 UNKNOWN/HOLD다. 이전 #112와 기존 release의 PASS를 새 adoption Gate PASS로 재사용하지 않는다.

## Repository evidence

2026-10-14 15:05:27 KST 원격 재확인 기준의 dated snapshot:

- `main=c72e957f21cc549fae2c77b8fa946c867aef8953`, tree `ab70ad922c62c0565423aaf3b26534c67867437a`.
- `dev=6622479a22e241a7a1392c04886a65c065d307b1`, tree `ea1636311a222bb54db2b6475e56c10db0853c9c`; main은 dev의 조상.
- [PR #112](https://github.com/xxh3898/form-dock/pull/112)는 MERGED. 결과 commit의 한 부모와 PR head/tree equality로 squash provenance를 확인했다. [Exact post-merge push/dev Validate](https://github.com/xxh3898/form-dock/actions/runs/38507752134)는 2026-10-14 12:43:52–12:47:05 KST, 핵심 3 check SUCCESS / ARM64 expected SKIPPED다.
- [Issue #97](https://github.com/xxh3898/form-dock/issues/97)은 2026-10-14 14:31:10 KST에 completed로 종료됐다. Issue #113은 OPEN이고 열린 PR은 없다. 이 상태 변경은 이번 로컬 구현이 수행한 것이 아니다.
- dev required checks는 Backend/Frontend/Infrastructure. Admin enforcement 및 force/deletion 금지는 확인했다. Conversation resolution은 비활성으로 중앙 branch contract gap이다. Main은 ARM64 Release Artifact도 요구한다. [Protection API](https://api.github.com/repos/xxh3898/form-dock/branches/dev/protection)의 시점 관측이며 영구 authority가 아니다.
- Production Environment에는 branch policy가 있다. [Environment API](https://api.github.com/repos/xxh3898/form-dock/environments/Production)의 존재만으로 Secret scope, 실제 approval 또는 현재 host 상태를 증명하지 않는다.

## Production evidence

역사적 evidence는 [D2A](docs/06-quality/phase-5-d2a-local-production-bootstrap-evidence.md), [D2B](docs/06-quality/phase-5-d2b-public-homeops-activation-evidence.md), [CD activation](docs/06-quality/phase-5-production-cd-control-plane-activation-evidence.md), [Phase 6-A launch](docs/06-quality/phase-6a-dogfooding-launch-evidence.md)를 원본 그대로 보존한다.

| Axis | Historical accepted evidence | 이번 로컬 구현의 current verified evidence |
| --- | --- | --- |
| Runtime/schema/route/lock | 2026-08-30 accepted snapshot | UNKNOWN — host/private state 미조회 |
| Backup/restore | 과거 logical backup verify와 scratch restore | 최신 backup·schedule·RPO/RTO UNKNOWN |
| HomeOps | 과거 healthy/incident0, notification disabled | 현재 health/incident/delivery UNKNOWN |
| CD automation | 과거 ARMED, 첫 application auto deploy pending | 현재 actual state UNKNOWN; enable/disable 미실행 |
| Real Survey | 2026-08-30 OPEN, 17문항/80선택지, target Survey synthetic 제출0 | 현재 status/구성/count UNKNOWN |

기존 Phase 6-A 문서의 **2026-10-14 11:38 KST dated audit**는 anonymous public page/API GET 403과 원인 미확인을 기록한다. 이를 영구 장애나 Survey CLOSED로 해석하지 않는다. 이번 구현에서는 public GET을 반복하거나 인증 자료를 새로 수집하지 않았다. 현재 Response count UNKNOWN, authenticated read NOT_AVAILABLE, trusted questionnaire semantic parity NOT_REPRODUCED다. 과거 count0/hash를 현재 값으로 덮어쓰지 않는다.

Legacy reconstruction의 `firstActivation`은 existing data 및 current/previous UNKNOWN을 선언한다. Fresh activation을 재실행하지 않는다. Desired automation default disabled는 중앙 lifecycle 정책이며 기존 ARMED 설정을 끄라는 지시가 아니다. 기존 activation reference는 historical authority이고 현재 G8 acceptance나 최초 application 자동 배포 성공을 새로 증명하지 않는다.

## Adoption gaps

Machine gap ID와 owner/date/status는 profile의 `adoptionGaps`가 소유한다. 아래 표는 source 근거, 미충족 요구와 후속 승인 경계를 설명한다. `exceptions: []`; 기존 DEFERRED_ACCEPTED_RISK는 Golden Path exception이 아니다.

| Gap | 수준 | Source / 미충족 요구 | 후속 범위·승인 |
| --- | --- | --- | --- |
| FD-GAP-001/002 | 높음 | Daily/recent7 및 repository backup manifest 계약이 Tier3 목표·semantic/runtime/schema/count 요구와 다름; 최신 운영 검증 UNKNOWN | Backup 계약/tooling PR, 실제 schedule/backup/retention은 별도 Ops |
| FD-GAP-003 | 높음 | Distinct filesystem copy만 구현; encrypted independent offsite/key/fresh retrieval 미확인 | Target/key custody 결정, tooling, 실제 copy/retrieval/restore 승인 분리 |
| FD-GAP-004 | 높음 | Fresh-host/host-loss와 measured RPO/RTO 절차/evidence 부족 | DR 계약 PR, 자원·restore/drill 별도 승인 |
| FD-GAP-005 | 높음 | Independent monitoring/delivery 미검증, historical notification disabled | HomeOps/notification 결정·활성화 별도 승인 |
| FD-GAP-006/007/008 | 높음 | Pending-before-backup, 공유 lock 범위, recurring fingerprint/replay 차이 | 각각 국소 deploy-control PR와 non-production fixture; 운영 설치/활성화 별도 |
| FD-GAP-009 | 높음 | Startup Flyway; one-shot migration/validate-only 분리 부재 | 별도 API/runtime contract PR; 실제 migration 미포함 |
| FD-GAP-010 | 높음 | Bounded post-commit observation 부재; 현재 공개 접근 원인 미확인 | Observation PR, bounded read-only 운영 조사 |
| FD-GAP-011 | 중간 | Mutable base tag, 명시적 SBOM/provenance linkage와 artifact 보존 enforcement 미검증 | Dependency/CI/artifact PR; registry mutation 별도 |
| FD-GAP-012/013 | 중간 | Conversation enforcement 비활성, profile/scan CI 미편입 | GitHub settings와 CI PR 승인 분리 |
| FD-GAP-014 | 중간 | Credential TTL/cleanup/recovery 근거 부족 | Credential contract 및 별도 security Ops |
| FD-GAP-015 | 높음 | PrivacyNotice/count UNKNOWN, retention/purge deferred, Response delete/exclude 부재 | Data Owner 정책·운영 결정; Phase6-B/기능 권한 확대 금지 |
| FD-GAP-016 | 높음 | Current runtime/backup/incident/automation 및 최초 auto application deploy 미확인 | Bounded evidence 수집과 별도 G6~G8 승인 |
| FD-GAP-017/018 | 중간 | CD path UNKNOWN 및 해결일/parameter/일부 binding 미정 | 후속 Owner 계획·contract 정정; classifier/settings 변경 미포함 |

## Backup gaps

[Backup recovery](docs/07-operations/backup-recovery.md)의 daily/recent7 및 과거 local verification은 desired Tier3 정책 충족을 증명하지 않는다. Latest BACKUP_VERIFIED, 실제 schedule/natural completion, count/schema/runtime manifest coverage, encryption, offsite 및 restore freshness는 UNKNOWN이다. Same-disk directory나 iCloud sync를 독립 DR로 취급하지 않는다. 기존 accepted risk를 DR PASS로 바꾸지 않는다.

## Deployment gaps

[Wrapper](infra/production/forced-command.sh.example)는 stage/pending을 만든 뒤 [worker](infra/production/deploy-release.sh)를 호출하며 worker는 이후 lock/backup을 검사한다. [중앙 DEP-001/002/007](https://github.com/xxh3898/platform-golden-path/blob/851b9066cc42e6a5cc1bc808d0ff9be40e4b8a7c/04-deployment/deployment-standard.md)과의 순서·공유 lock·reconcile-only replay 차이를 기록한다. 초기 activation operationId와 recurring 계약을 혼동하지 않는다. 기존 rollback/DB volume 보존/terminal HomeOps transaction은 유지한다. 새 failed-deploy reconciliation 절차와 operation record mapping은 아직 구현하지 않았다.

## Host loss

Host-loss, site-loss, application rollback, schema/data recovery를 구분한다. Current primary failure domain 밖의 verified 복구점, annual host-loss drill, tamper-evident evidence와 measured objectives는 UNKNOWN이다. 완성된 recovery runbook reference가 없는 gap이며 이 절은 restore 실행 절차가 아니다.

## Fresh host bootstrap

Old host 없이 artifact/config/source, infrastructure dependency와 data를 복구할 exact bootstrap 순서/evidence는 아직 재구성하지 않았다. Cloudflare/HomeOps와 key recovery dependency도 inventory 대상이다. D2A first activation helper를 existing data에 재실행하는 방법으로 대체하지 않는다.

## Credential recovery

Source에 protected Environment/Tailscale identity/restricted SSH/private host configuration 계약이 있다. 현재 installed source/permission, compromise revoke·reissue 순서, headless recovery rehearsal은 UNKNOWN이다. 이 절은 missing project procedure를 설명하며 실제 Secret/key/private path를 포함하지 않는다.

## Offsite recovery

[copy-off-host.sh](infra/backup/copy-off-host.sh)는 distinct configured filesystem copy 의미만 제공한다. Independent encrypted destination, separate key custody, pinned clean retrieval tool, alternate destination 및 retrieval→decrypt→checksum→대표 restore evidence는 미확인이다. Target/provider/cost/authority는 별도 Owner 결정이며 이 절은 retrieval tool이 아니다.

## Privacy

[Retention](docs/04-data/retention.md)은 보존 기간/purge/backup retention/privacy template 결정을 deferred한다. Creator 질문으로 연락처 등 개인정보가 수집될 수 있으며 삭제/exclude 기능은 없다. Backup restore 후 재삭제/보존 책임을 별도로 결정해야 한다. 원문 Response·CSV·개인식별정보는 이번 작업에서 읽거나 기록하지 않는다. Response가 UNKNOWN이라고 데이터 없음으로 처리하지 않는다.

## CD impact

[Classifier](infra/cd/classify-change.sh)는 `.ops/project.yaml`, `PROJECT-OPS.md`를 UNKNOWN으로 분류한다. AGENTS.md만 DOCS_META_ONLY이므로, 후속 main cumulative diff에 새 경로가 포함되면 UNKNOWN_PATH_FAIL_CLOSED로 publish/deploy HOLD될 수 있다. 더 높은 우선순위 DEPLOY_CONTROL/MIGRATION_OR_DATA도 HOLD다. Classifier/workflow/kill switch를 수정하거나 우회하지 않으며 테스트를 위해 Release/deploy하지 않는다.

## Validation and recovery

승인된 중앙 exact clean checkout의 실제 `--help`로 CLI를 확인하고 working-tree profile shape를 검사한다. YAML/schema/enum/conditional validity는 G0/G5~G8 또는 COMPLIANT가 아니다.

2026-10-14 KST 로컬 검증에서 `PROFILE_VALIDATION=PASS PROFILE=INPUT ERRORS=0`을 확인했다. 변경 allowlist 3개, UTF-8/LF/final newline, fence/link, 실제 source reference 및 Secret/PII 점검을 통과했다. 기존 Markdown 66개와 AGENTS의 원래 절은 byte-identical이며 code/API/schema/CI 변경은 0이다. 변경 경로 3개만 넣은 classifier fixture는 `classification=UNKNOWN`, `deployEligible=false`, `holdReason=UNKNOWN_PATH_FAIL_CLOSED`였다. 이 fixture는 실제 Release나 cumulative baseline 평가가 아니다.

```text
PINNED_STATIC_COMPLIANCE = NOT RUN — SUBJECT_COMMIT_NOT_AUTHORIZED
NEW_HEAD_HOSTED_CI      = NOT RUN — PR_NOT_AUTHORIZED
PRODUCTION_ACCEPTANCE  = UNKNOWN / HOLD
```

Profile 포함 subject commit 없이 과거 HEAD에 대해 static PASS를 주장하지 않는다. 기존 CI 결과도 새 local 변경의 CI가 아니다. 이번 구현은 local changes를 보존하고 종료한다. Owner review 후 commit/push/PR을 별도 승인받는다.

복구 경계는 승인된 문서 변경의 비파괴적 revert다. Uncommitted 변경은 Owner 지시에 따라 해당 세 파일만 보정하며 사용자 파일을 reset/clean/stash하지 않는다. Force push/history rewrite/PR merge는 금지다. Production Survey/Data rollback·cleanup이나 backup restore는 문서 변경 복구에 포함하지 않는다.
