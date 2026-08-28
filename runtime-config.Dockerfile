FROM alpine:3.23

ARG REVISION

LABEL org.opencontainers.image.revision="${REVISION}"
LABEL io.formdock.runtime-config.project="form-dock"

WORKDIR /runtime
COPY infra/compose.production.yaml ./compose.yaml
COPY infra/production/deploy-release.sh ./scripts/deploy-release.sh
COPY infra/production/report-homeops-deployment.sh ./scripts/report-homeops-deployment.sh
COPY infra/backup/common.sh ./scripts/common.sh
COPY infra/backup/verify.sh ./scripts/verify-backup.sh
COPY infra/delivery/common.sh ./scripts/delivery-common.sh
RUN printf '%s\n' "${REVISION}" | grep -Eq '^[0-9a-f]{40}$' \
    && printf '%s\n' "${REVISION}" > ./revision \
    && chmod 600 ./compose.yaml ./revision ./scripts/common.sh ./scripts/delivery-common.sh \
    && chmod 700 ./scripts/deploy-release.sh \
      ./scripts/report-homeops-deployment.sh \
      ./scripts/verify-backup.sh
