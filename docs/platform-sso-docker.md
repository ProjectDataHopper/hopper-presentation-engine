# Platform SSO, ops dashboards, and Docker

How **hopper-presentation-engine** joins the Data Hopper fleet (hopperShip, hopperHarbor, hopperFrontend).

## Principles

1. **Jersey stays** — presentation is not Spring Boot; it validates the **same Keycloak JWTs** as Ship/Harbor.
2. **One realm (`hopper`)** — roles `viewer` / `operator` / `admin` map to Hopper `VIEWER` / `AUTHOR` / `ADMIN` via `auth.role-aliases`.
3. **Per-Ship presentation** first — one presentation container per Hop environment Compose stack.
4. **Ship REST** is the preferred dashboard data API; Neo4j is internal read-only for aggregates later.

## Role map

| Keycloak realm role | Presentation `HRole` | Typical Ship access (planned) |
|---------------------|----------------------|--------------------------------|
| `viewer` | `VIEWER` | Read artifacts & runs |
| `operator` | `AUTHOR` | + invoke / operate |
| `admin` | `ADMIN` | Full admin |

Configure:

```properties
auth.role-aliases=viewer:VIEWER,operator:AUTHOR,admin:ADMIN
auth.roles-claim=realm_access.roles
```

Default aliases are already applied when the property is unset.

## OAuth wizard

Admin UI → **Auth & OAuth** → provider **Data Hopper Keycloak** (`hopper-keycloak`):

- Base URL: `http://keycloak:8080` (internal) or public host
- Realm: `hopper`
- Client: `hopper-ui` (PKCE)
- Audience: `hopper-presentation`
- Redirect: `http://localhost:8088/hopper/api/auth/callback` (dev)

## Health

```http
GET /hopper/api/system/health
```

Public JSON `{ "status": "UP", ... }` for Docker `HEALTHCHECK`.

## Docker

### Build image

From monorepo root (`hopper-presentation-engine`):

```bash
docker build -f hopper-presentation-rest/Dockerfile -t hopper-presentation:local .
```

### Compose fragment

`docker/compose.fragment.yml` adds `keycloak` + `presentation` on network `hopperShip-internal`.

From **hopperShip**:

```bash
# build presentation image first (context = presentation monorepo)
cd ../hopper-presentation-engine
docker build -f hopper-presentation-rest/Dockerfile -t hopper-presentation:local .

cd ../hopperShip/docker
docker compose -f docker-compose.yml \
  -f ../../hopper-presentation-engine/docker/compose.fragment.yml \
  up -d
```

| Service | Host port (default) |
|---------|---------------------|
| presentation | 8088 → 8080 |
| keycloak | 8081 → 8080 |
| ship api | 20000 → 8080 |

Realm import: `docker/keycloak/hopper-realm.json` (users `admin`/`admin`, `viewer`/`viewer`).

### Config volume

- Properties: `/config/hopper-presentation.properties` (`HOPPER_REST_CONFIG_PATH`)
- Metadata: `/hopper-data/metadata`

Enable OAuth in properties after Keycloak is healthy (or apply **Data Hopper Keycloak** in the admin panel).

## Seamless login (SSO)

| Phase | Mechanism |
|-------|-----------|
| **MVP** | Keycloak SSO session: log into presentation or Frontend once; second app reuses IdP session (silent OIDC) |
| **Later** | Frontend embeds `/hopper/api/render/...` or reverse-proxies under one origin; optional APISIX route `/p/*` |

API clients use `Authorization: Bearer` with tokens from the same realm.

## Ops dashboards (P2)

Seed pack lives in `hopper-presentation-rest/docker/metadata-ops/` (also copied under `config/metadata/` for local jetty).

| Presentation | Source | Notes |
|--------------|--------|--------|
| `ops-run-status` | `ops-runs-sample` CSV + filters | Offline demo; auto-refresh 30s |
| `ops-failures` | filter `status=FAILED` | Offline |
| `ops-workers` | aggregate by `workerHost` | Bar chart of run counts |
| `ops-ship-live` | `ship-runs` REST | `${HOPPER_SHIP_API_URL}/api/runs` + caller Bearer |

### REST connector auth

- **Use caller Bearer token** — sends `Authorization: Bearer` from the logged-in principal (`bearer_token` attribute set at OIDC/JWT login).
- **Authorization header** — static / `${ENV}` service token fallback.
- **Root JSON array** — leave **Rows JSON path** empty for Ship-style `GET /api/runs` responses.

### Variables

| Variable | Purpose | Default |
|----------|---------|---------|
| `HOPPER_SHIP_API_URL` | Ship API base (no trailing slash) | `http://localhost:20000` |
| `HOPPER_METADATA_PATH` | Metadata root for sample CSV paths | set by server from `metadata.path` |

### Auto-refresh

Presentation field `autoRefreshSeconds` (or view URL `?refresh=15`) reloads the page in view mode (minimum 5s).

## Related

- [admin-panel.md](admin-panel.md)
- [oauth-providers.md](oauth-providers.md)
- [security-and-audit.md](security-and-audit.md)
- Sibling: `../hopperShip/docker/docker-compose.yml`, `../hopperShip/AGENTS.md`
