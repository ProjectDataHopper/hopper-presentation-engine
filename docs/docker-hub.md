# projectdatahopper/presentation-engine

**Official container image for the Data Hopper Presentation Engine** — metadata-driven reports and interactive dashboards rendered as SVG on the server.

[Data Hopper](https://www.data-hopper.com) · Image: [`projectdatahopper/presentation-engine`](https://hub.docker.com/r/projectdatahopper/presentation-engine)

---

## What it is

The Presentation Engine turns **JSON metadata** (presentations, connectors, themes) into **server-side SVG pages** that run in the browser. Typical use cases:

- Operational dashboards (pipeline/workflow runs, failures, worker load)
- Tabular and chart reports over SQL, REST APIs, CSV, and chained transforms
- A browser **viewer** and **canvas editor** for designing presentations
- Optional **OAuth2 / OIDC** login (Keycloak, Google, Entra, Okta, Auth0, …)
- Admin panel for security, roles, ACLs, settings, and render-cache ops

It is **not** Spring Boot: the image runs **Apache Tomcat 10.1** on **Java 21** (Eclipse Temurin) with a **Jersey** REST API under `/hopper/api/`.

| Surface | URL (default host port 8088 → container 8080) |
|---------|------------------------------|
| Home / presentation list | http://localhost:8088/hopper/api/render/main/ |
| Admin panel | http://localhost:8088/hopper/api/static/admin/ |
| Health (public) | http://localhost:8088/hopper/api/system/health |
| API base | http://localhost:8088/hopper/api/ |

---

## Quick start

### 1. Run the container

```bash
docker pull projectdatahopper/presentation-engine:latest

docker run --rm -p 8088:8080 \
  --name hopper-presentation \
  projectdatahopper/presentation-engine:latest
```

Wait until healthy (first start can take ~1–2 minutes while TomEE boots):

```bash
curl -s http://localhost:8088/hopper/api/system/health
# {"status":"UP", ...}
```

Open the home page:

**http://localhost:8088/hopper/api/render/main/**

By default authentication is **off** (open API) so you can explore locally. For production, enable OAuth2 (see below).

### 2. Persist metadata (recommended)

Presentations and connectors live under `metadata.path` (default `/hopper-data/metadata` in the image):

```bash
mkdir -p ./hopper-data/metadata

docker run --rm -p 8088:8080 \
  --name hopper-presentation \
  -v "$(pwd)/hopper-data/metadata:/hopper-data/metadata" \
  -e HOPPER_METADATA_PATH=/hopper-data/metadata \
  projectdatahopper/presentation-engine:latest
```

Structure (created as you save from the UI or by copying a seed pack):

```text
metadata/
  presentation/     # *.json presentations
  connector/        # data sources / transforms
  theme/
  hopper-database-connection/
  sample/           # optional CSV samples for ops demos
```

### 3. Custom config

Mount your own properties file over `/config/hopper-presentation.properties`:

```bash
docker run --rm -p 8088:8080 \
  -v "$(pwd)/hopper-presentation.properties:/config/hopper-presentation.properties:ro" \
  -v "$(pwd)/hopper-data/metadata:/hopper-data/metadata" \
  -e HOPPER_REST_CONFIG_PATH=/config \
  -e HOPPER_METADATA_PATH=/hopper-data/metadata \
  projectdatahopper/presentation-engine:latest
```

Minimal properties:

```properties
metadata.path=/hopper-data/metadata
cors.allow.origin=true
```

---

## Docker Compose

```yaml
services:
  presentation:
    image: projectdatahopper/presentation-engine:latest
    ports:
      - "8088:8080"
    environment:
      HOPPER_REST_CONFIG_PATH: /config
      HOPPER_METADATA_PATH: /hopper-data/metadata
      # Optional: Data Hopper Ship API for live ops dashboards
      HOPPER_SHIP_API_URL: http://api:8080
    volumes:
      - presentation-metadata:/hopper-data/metadata
      # - ./hopper-presentation.properties:/config/hopper-presentation.properties:ro
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O - http://127.0.0.1:8080/hopper/api/system/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 90s
    restart: unless-stopped

volumes:
  presentation-metadata:
```

Join the same Docker network as [hopperShip](https://github.com/ProjectDataHopper) (or your Hop environment stack) to call Ship’s `/api/runs` from ops presentations.

---

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `HOPPER_REST_CONFIG_PATH` | `/config` | Directory containing `hopper-presentation.properties` |
| `HOPPER_METADATA_PATH` | (from properties) | Metadata root; also used as `${HOPPER_METADATA_PATH}` in connectors |
| `HOPPER_SHIP_API_URL` | `http://localhost:20000` | Base URL of hopperShip API (no trailing slash) for REST connectors |
| `CATALINA_OPTS` | includes `-DHOPPER_REST_CONFIG_PATH=…` | JVM options |

Secrets (OAuth client secret, etc.) should be passed as **environment variables** and referenced from properties as `${ENV_NAME}` — do not bake secrets into the image.

---

## Authentication (optional)

Out of the box the API is open for demos. For real deployments:

1. Open **Admin** → **Auth & OAuth** (or set properties).
2. Choose a provider (e.g. **Data Hopper Keycloak**, Google, Entra ID).
3. Set `auth.enabled=true`, `auth.mode=oauth2`, issuer, client id, redirect URI.
4. Map IdP roles with:

```properties
auth.role-aliases=viewer:VIEWER,operator:AUTHOR,admin:ADMIN
```

Admin UI (when you have `ADMIN`):  
http://localhost:8088/hopper/api/static/admin/

Shared SSO with hopperShip / hopperHarbor / hopperFrontend uses the same Keycloak realm (`hopper`) pattern. See project docs for the full platform contract.

---

## Ops dashboards

The engine can show **pipeline / web-service run** dashboards fed by:

- **Sample CSV** (offline demos), or  
- **Live Ship REST** (`GET ${HOPPER_SHIP_API_URL}/api/runs`) with optional **caller Bearer** token propagation for authenticated users.

Seed packs may include presentations such as `ops-run-status`, `ops-failures`, `ops-workers`, `ops-ship-live`. Auto-refresh is supported via presentation `autoRefreshSeconds` or `?refresh=30` on the view URL.

---

## Architecture (one paragraph)

**Connectors** pull or transform tabular data (SQL, REST, CSV, filter, aggregate, chain, …). **Components** (table, crosstab, bar/line/pie charts, labels, …) layout and draw into SVG. **Presentations** compose pages, themes, parameters, and interactions as Hop-style metadata JSON. The REST tier serves rendered pages, the edit canvas, undo history, security APIs, and the admin panel.

```text
Browser  →  /hopper/api/  →  TomEE + Jersey WAR
                              ├─ render (SVG pages)
                              ├─ edit (canvas + forms)
                              ├─ auth (OIDC / JWT)
                              └─ admin (settings, roles, ACLs, cache)
                              ↓
                         metadata JSON + connectors → data sources
```

---

## Tags

| Tag | Meaning |
|-----|---------|
| `latest` | Current published build (dev-friendly) |
| `x.y.z` / `x.y.z-SNAPSHOT` | Versioned builds aligned with project releases |

Prefer pinning a version tag in production.

---

## Ports & health

| Port | Service |
|------|---------|
| **8080** (container) | HTTP (map e.g. `8088:8080`) |

```bash
curl -sf http://localhost:8088/hopper/api/system/health | jq .
```

---

## Requirements

- Docker Engine 20+ (or compatible runtime)
- ~1 GB RAM recommended for Tomcat + first render
- **Java 21** is bundled in the image (host JDK not required)

---

## Source & license

- **Source:** Data Hopper presentation monorepo (`hopper-presentation-engine`)
- **Website:** [data-hopper.com](https://data-hopper.com)
- **License:** Apache License 2.0 (see repository `LICENSE` / NOTICE)

---

## Support

- Issues and contributions: project GitHub / Data Hopper channels  
- Configuration reference: `hopper-presentation.properties`, admin **Settings** schema  
- Platform / SSO / Ship integration notes: repository `docs/platform-sso-docker.md`

---

## Disclaimer

This image is intended for the Data Hopper ecosystem. Enable authentication and TLS (reverse proxy) before exposing it beyond a trusted network. Do not commit OAuth secrets into images or public volumes.
