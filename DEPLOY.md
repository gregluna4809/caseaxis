# CaseAxis Deployment Runbook

## Prerequisites

- Droplet with Docker installed
- Existing Caddy reverse proxy at `/opt/apps/reverse-proxy`
- DNS A record for `caseaxis.pulse-forge.com` pointing to droplet IP, Cloudflare proxy OFF (gray cloud)

## Generate secrets locally (PowerShell)

DB password (32 alphanumeric chars):

```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
```

JWT secret (base64, 64 bytes):

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

## First-time deployment

1. SSH to droplet: `ssh caseaxis`
2. `cd /opt/apps`
3. `git clone https://github.com/gregluna4809/caseaxis.git`
4. `cd caseaxis`
5. `cp .env.production.example .env.production`
6. `nano .env.production` - paste in the secrets generated above and set `CASEAXIS_IMAGE_TAG=sha-<tested-commit-sha>`
7. Authenticate Docker to GHCR with a GitHub token that has package read access:

```bash
echo "$GHCR_READ_TOKEN" | docker login ghcr.io -u gregluna4809 --password-stdin
```

8. Pull the tested immutable images:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml pull backend frontend
```

9. Start the stack without building on the host:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
```

10. `docker compose --env-file .env.production -f docker-compose.prod.yml logs -f backend` - watch until `Started CaseAxisApplication`, Ctrl+C to exit logs

Production images are published by CI to GHCR after the backend, frontend, and dependency scan jobs pass on `main`. Each release produces immutable commit tags such as `ghcr.io/gregluna4809/caseaxis-backend:sha-<commit>` and `ghcr.io/gregluna4809/caseaxis-frontend:sha-<commit>`, plus a moving `prod` tag for operator visibility. Deploy using the `sha-<commit>` tag, not `prod`.

## Connect Caddy to the new network

Edit `/opt/apps/reverse-proxy/docker-compose.yml` to add the `caseaxis_default` network.

Add this under `networks:` at the bottom of the file:

```yaml
  caseaxis_default:
    external: true
```

Add this under the `caddy` service's `networks` list:

```yaml
      - caseaxis_default
```

Apply the change:

```bash
docker compose -f /opt/apps/reverse-proxy/docker-compose.yml up -d
```

## Update the Caddyfile

Append this block to `/opt/apps/reverse-proxy/Caddyfile`:

```caddyfile
caseaxis.pulse-forge.com {
    handle /api/* {
        reverse_proxy caseaxis-backend:8080
    }
    handle /actuator/* {
        reverse_proxy caseaxis-backend:8080
    }
    handle {
        reverse_proxy caseaxis-frontend:80
    }
}
```

Reload Caddy:

```bash
docker exec caddy caddy reload --config /etc/caddy/Caddyfile
```

## Seed production data

Run this one-shot Python container on the `caseaxis_default` network. Substitute `USER`, `PASS`, and `DBNAME` from `.env.production`:

```bash
docker run --rm --network caseaxis_default -v /opt/apps/caseaxis/tools:/tools -e DB_URL=postgresql://USER:PASS@db:5432/DBNAME python:3.11-slim bash -c "pip install Faker 'psycopg[binary]' && python /tools/seed_bir_demo.py --reset-demo --organizations 250 --clients 25000 --cases 75000 --notes 150000 --tasks 100000 --attachments 50000"
```

## Verify

- `curl https://caseaxis.pulse-forge.com/actuator/health`
- Expected: `{"status":"UP"}`
- Open `https://caseaxis.pulse-forge.com` in browser, confirm UI loads

## Disaster recovery

CaseAxis uses two backup layers:

- DigitalOcean Droplet backups for host-level recovery.
- Daily PostgreSQL logical backups from the `db` service backed by the `caseaxis_pgdata` volume, uploaded to DigitalOcean Spaces or another S3-compatible off-host target.

### Enable DigitalOcean Droplet backups

1. In the DigitalOcean control panel, open the production CaseAxis Droplet.
2. Open the Droplet's Backups page.
3. Enable automated Droplet backups for the Droplet.
4. Record the configured backup window and retention shown in the control panel.
5. Keep Droplet backups enabled as an infrastructure safety net; database point-in-time recovery depends on the logical dump process below.

### Configure off-host PostgreSQL backups

Install the AWS CLI on the Droplet if it is not already present:

```bash
sudo apt-get update
sudo apt-get install -y awscli
```

Create a root-owned environment file for backup credentials:

```bash
sudo install -d -m 0700 /etc/caseaxis
sudo nano /etc/caseaxis/backup.env
sudo chmod 0600 /etc/caseaxis/backup.env
```

Populate `/etc/caseaxis/backup.env` with real values. Do not commit this file:

```bash
CASEAXIS_APP_DIR=/opt/apps/caseaxis
CASEAXIS_COMPOSE_FILE=/opt/apps/caseaxis/docker-compose.prod.yml
CASEAXIS_ENV_FILE=/opt/apps/caseaxis/.env.production
BACKUP_WORKDIR=/var/backups/caseaxis/postgres
BACKUP_BUCKET=caseaxis-backups
BACKUP_PREFIX=prod/postgres
LOCAL_RETENTION_DAYS=7
AWS_ACCESS_KEY_ID=REPLACE_WITH_SPACES_KEY
AWS_SECRET_ACCESS_KEY=REPLACE_WITH_SPACES_SECRET
AWS_DEFAULT_REGION=nyc3
AWS_ENDPOINT_URL=https://nyc3.digitaloceanspaces.com
```

Configure the Spaces bucket lifecycle rule for at least 30 days of retention. Recommended production cadence:

- DigitalOcean Droplet backup: enabled continuously, with the backup window and provider retention recorded from the control panel.
- PostgreSQL logical backup: daily at 03:15 UTC, with a 20-minute randomized delay.
- Off-host retention: at least 30 days.
- Local Droplet retention: 7 days, only as a short recovery cache.
- Manual restore test: at least once per quarter and after material schema changes.

Install and enable the systemd timer:

```bash
sudo cp /opt/apps/caseaxis/deploy/systemd/caseaxis-backup.service /etc/systemd/system/
sudo cp /opt/apps/caseaxis/deploy/systemd/caseaxis-backup.timer /etc/systemd/system/
sudo chmod +x /opt/apps/caseaxis/deploy/backup/backup-postgres.sh
sudo systemctl daemon-reload
sudo systemctl enable --now caseaxis-backup.timer
systemctl list-timers caseaxis-backup.timer
```

Do not run the backup script until the Spaces bucket, lifecycle rule, and `/etc/caseaxis/backup.env` are configured.

### Restore from logical backup

The first real restore test is manual and must be performed by the operator on a disposable Droplet or isolated database. Do not restore over production until the target has been confirmed.

1. Provision an isolated Droplet or database host.
2. Clone the repository and create `.env.production` with restore-target credentials.
3. Start only the database service:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d db
```

4. Download the selected dump from Spaces:

```bash
aws --endpoint-url "$AWS_ENDPOINT_URL" s3 cp s3://caseaxis-backups/prod/postgres/caseaxis-caseaxis-YYYYMMDDTHHMMSSZ.dump /tmp/caseaxis-restore.dump
```

5. Restore into the target database:

```bash
source .env.production
docker compose --env-file .env.production -f docker-compose.prod.yml exec -T -e PGPASSWORD="$POSTGRES_PASSWORD" db pg_restore --clean --if-exists --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" < /tmp/caseaxis-restore.dump
```

6. Start the backend and frontend on the isolated host:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml pull backend frontend
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
```

7. Verify `/actuator/health`, login, dashboard totals, case search, and a representative case detail page.
8. Record the restore result below.

Last restore test: `TODO: YYYY-MM-DD, backup timestamp, operator, result, issues found`

## Troubleshooting

- Blank frontend: check browser console; verify Caddyfile reload succeeded
- Backend won't start: `docker compose -f docker-compose.prod.yml logs backend`
- No HTTPS cert: `dig caseaxis.pulse-forge.com` to confirm DNS; ensure Cloudflare proxy is off (gray cloud)
