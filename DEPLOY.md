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
6. `nano .env.production` - paste in the secrets generated above
7. `docker compose -f docker-compose.prod.yml up -d --build`
8. `docker compose -f docker-compose.prod.yml logs -f backend` - watch until `Started CaseAxisApplication`, Ctrl+C to exit logs

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

## Troubleshooting

- Blank frontend: check browser console; verify Caddyfile reload succeeded
- Backend won't start: `docker compose -f docker-compose.prod.yml logs backend`
- No HTTPS cert: `dig caseaxis.pulse-forge.com` to confirm DNS; ensure Cloudflare proxy is off (gray cloud)
