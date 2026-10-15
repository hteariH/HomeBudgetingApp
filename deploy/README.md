# Deploying the project page

`personalmoney.app` serves the project's page (`site/`): what PersonalMoney is and how to
install it. The app itself is not hosted here; everyone runs their own instance from the
published image and `docker-compose.yml`.

The page is static. nginx serves the built files behind the shared `edge` Caddy that owns
ports 80/443 on the VPS, the same way the wordle and crocodile stacks do. Nothing here
publishes a host port.

## One-time setup

### 1. DNS

| Record | Value |
| --- | --- |
| `A` `personalmoney.app` | VPS IP |
| `A` `www.personalmoney.app` | VPS IP |

Certificates are issued by the edge Caddy on first request; nothing to do beyond DNS.

### 2. The edge network

The site container joins the external `edge` network, which is created once on the VPS
(it already exists wherever the other stacks run):

```bash
sudo docker network create edge
```

### 3. GitHub secrets

The site workflow needs only the SSH secrets, named the same way as the edge and wordle
repos so one set of values works everywhere: `SSH_HOST`, `SSH_USER`, `SSH_KEY` and
optionally `SSH_PORT`.

### 4. Stop the old app stack (once)

Before this site existed, `personalmoney.app` served the hosted app from `/opt/homebudget`.
Stop that stack by hand on the VPS:

```bash
cd /opt/homebudget && sudo docker compose down
```

Its database volume stays on disk after `down`. If you want a copy of what was in it, take a
final `mysqldump` before removing the volume; otherwise remove it with
`sudo docker volume rm homebudget_db_data`.

## What the deploy does

The site workflow (`.github/workflows/site.yml`) builds `site/` (`npm test`, `npm run build`)
and, on the VPS:

- copies `site/dist/` to `/opt/personalmoney-site/dist`;
- copies `deploy/docker-compose.site.yml` to `/opt/personalmoney-site/docker-compose.yml`;
- copies `deploy/personalmoney.caddy` to `/opt/edge/conf.d/`;
- runs `docker compose up -d` in `/opt/personalmoney-site` and reloads the shared Caddy.

The container is `personalmoney-site`, reachable on the `edge` network by that alias from
`deploy/personalmoney.caddy`.
