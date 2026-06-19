#!/usr/bin/env bash
set -Eeuo pipefail

# CaseAxis PostgreSQL logical backup.
# Runs pg_dump against the docker-compose db service backed by the caseaxis_pgdata volume,
# then uploads the compressed custom-format dump to S3-compatible off-host storage.

APP_DIR="${CASEAXIS_APP_DIR:-/opt/apps/caseaxis}"
COMPOSE_FILE="${CASEAXIS_COMPOSE_FILE:-${APP_DIR}/docker-compose.prod.yml}"
ENV_FILE="${CASEAXIS_ENV_FILE:-${APP_DIR}/.env.production}"
BACKUP_WORKDIR="${BACKUP_WORKDIR:-/var/backups/caseaxis/postgres}"
BACKUP_PREFIX="${BACKUP_PREFIX:-postgres}"
LOCAL_RETENTION_DAYS="${LOCAL_RETENTION_DAYS:-7}"

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 1
  fi
}

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Production env file not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

require_env POSTGRES_USER
require_env POSTGRES_PASSWORD
require_env POSTGRES_DB
require_env BACKUP_BUCKET
require_env AWS_ACCESS_KEY_ID
require_env AWS_SECRET_ACCESS_KEY
require_env AWS_DEFAULT_REGION
require_env AWS_ENDPOINT_URL

mkdir -p "${BACKUP_WORKDIR}"
chmod 0700 "${BACKUP_WORKDIR}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_name="caseaxis-${POSTGRES_DB}-${timestamp}.dump"
backup_path="${BACKUP_WORKDIR}/${backup_name}"
s3_uri="s3://${BACKUP_BUCKET}/${BACKUP_PREFIX}/${backup_name}"

cd "${APP_DIR}"

echo "Creating PostgreSQL custom-format backup: ${backup_path}"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T \
  -e PGPASSWORD="${POSTGRES_PASSWORD}" \
  db pg_dump \
    --username="${POSTGRES_USER}" \
    --dbname="${POSTGRES_DB}" \
    --format=custom \
    --compress=9 \
    --no-owner \
    --no-acl \
  > "${backup_path}"

chmod 0600 "${backup_path}"

echo "Uploading backup to ${s3_uri}"
AWS_EC2_METADATA_DISABLED=true aws --endpoint-url "${AWS_ENDPOINT_URL}" \
  s3 cp "${backup_path}" "${s3_uri}" \
  --only-show-errors

echo "Pruning local backups older than ${LOCAL_RETENTION_DAYS} days from ${BACKUP_WORKDIR}"
find "${BACKUP_WORKDIR}" -type f -name "caseaxis-*.dump" -mtime "+${LOCAL_RETENTION_DAYS}" -delete

echo "Backup completed: ${s3_uri}"
