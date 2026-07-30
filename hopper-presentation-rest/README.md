# Hopper Presentation REST

REST API for the [Hopper Presentation Engine](https://github.com/mattcasters/hopper-presentation-core): metadata access and **server-side SVG** presentation rendering.

## Platform

| Requirement | Version |
|-------------|---------|
| Java | **21** |
| Apache Hop | **2.18.1** |
| hopper-presentation-core | **1.0.0-SNAPSHOT** |

## Steps to get going locally

1. Build and install hopper-presentation-core:

   ```bash
   cd ../hopper-presentation-core && mvn clean install
   ```

2. Start hopper-presentation-rest (config directory must contain `hopper-presentation.properties`):

   ```bash
   cd ../hopper-presentation-rest
   export HOPPER_REST_CONFIG_PATH="$PWD/src/test/resources"
   mvn clean install jetty:run -DHOPPER_REST_CONFIG_PATH="$HOPPER_REST_CONFIG_PATH"
   ```

3. Open the main page (note the **`/api`** segment):

   http://localhost:8080/hopper/api/render/main/

To debug, set `MAVEN_OPTS` to  
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`  
and attach a debugger to that port.

## Smoke test

See **[docs/smoke-test.md](docs/smoke-test.md)** for the full checklist (metadata, render UUID, SVG download).

Quick verify:

```bash
BASE=http://localhost:8080/hopper/api
curl -sS "$BASE/metadata/presentations/" | head
RID=$(curl -sS -X POST -H 'Content-Type: application/json' \
  -d '{"presentationName":"list-presentations","parameters":[],"reload":true}' \
  "$BASE/render/presentation")
curl -sS -o /tmp/page.svg "$BASE/render/page/$RID/SVG/0/"
grep -q '<svg' /tmp/page.svg && echo PASS
```

## Build and run the container (experimental)

```bash
docker build . -t hopper-presentation-rest
docker run -p 8080:8080 -v "$PWD/src/test/resources/:/hopper/" hopper-presentation-rest
```

Container config may still need `HOPPER_REST_CONFIG_PATH` / `metadata.path` alignment with the mounted volume.

## Authentication (optional)

By default the API is **open** (`auth.enabled` unset / false).

### Static-dev (local / CI)

```properties
auth.enabled=true
auth.mode=static-dev
auth.dev.user=admin
auth.dev.roles=ADMIN
# Optional per-request override (dev only):
# X-Hopper-User: alice
# X-Hopper-Roles: VIEWER
```

### OAuth2 resource server (JWT)

```properties
auth.enabled=true
auth.mode=oauth2
auth.issuer-uri=https://idp.example.com/realms/hopper
auth.audience=hopper-presentation
auth.username-claim=preferred_username
auth.roles-claim=realm_access.roles
```

Send `Authorization: Bearer <access_token>`. Invalid tokens → **401**; insufficient role for the action → **403**.

Roles: `VIEWER`, `AUTHOR`, `DATA_ENGINEER`, `ADMIN`, `AUDITOR`.

Optional **resource ACLs** (metadata `security-acl`) restrict named presentations/connections; admin API under `/api/security/acls/`. See [docs/security-and-audit.md](../docs/security-and-audit.md).

### Browser login (OIDC PKCE)

When `auth.mode=oauth2` and `auth.oidc.client-id` is set, the home page / editor redirect unauthenticated users to the IdP (Authorization Code + PKCE) and store an HttpOnly `HOPPER_SESSION` cookie.

```properties
auth.oidc.client-id=hopper-ui
auth.oidc.redirect-uri=http://localhost:8080/hopper/api/auth/callback
```

Useful endpoints: `/api/auth/config`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`.  
Administration panel (ADMIN): `/api/static/admin/` — OAuth wizard, settings, roles, users, ACLs, live usage.  
Live usage only (ADMIN/AUDITOR): `/api/static/admin-usage.html`.  
See [docs/admin-panel.md](../docs/admin-panel.md).

### Audit (usage lineage)

By default a **LoggingAuditSink** is registered. Optional JSONL file:

```properties
audit.enabled=true
audit.async=true
audit.bootstrap.logging=true
audit.bootstrap.jsonl.path=/tmp/hopper-audit.jsonl
```

Additional destinations via metadata key `audit-sink` (plugin ids `LoggingAuditSink`, `JsonlFileAuditSink`). See [docs/security-and-audit.md](../docs/security-and-audit.md).

## REST API

API root: **`http://localhost:8080/hopper/api`**

### Metadata

| Service | Type | Description |
|---------|:----:|-------------|
| `/metadata/types` | GET | List metadata type keys |
| `/metadata/list/{key}/` | GET | List element names for a type |
| `/metadata/{key}/{name}` | GET | Load one metadata element |
| `/metadata/{key}/` | POST | Save a metadata element |
| `/metadata/presentations/` | GET | High-level presentation list |

### Rendering

| Service | Type | Description |
|---------|:----:|-------------|
| `/render/main/` | GET | Main HTML shell (client opens a presentation list) |
| `/render/presentation` | POST | Render a presentation; body JSON with `presentationName`, optional `parameters`, `reload`, `layoutMode`, `viewportWidth`, `colorMode`. Returns render UUID (plain text) |
| `/render/presentation/soft` | POST | Soft re-render (JSON: `renderId`, continuous metrics, optional inline PNG) |
| `/render/info/pages/{renderId}` | GET | Number of pages for a rendering |
| `/render/info/layout/{renderId}` | GET | Layout metrics (continuous width/height, truncation) |
| `/render/page/{renderId}/{renderType}/{pageNumber}/` | GET | Page content (`SVG` or `HTML`) |
| `/render/p/{name}/{renderType}/{pageNumber}/` | GET | Bookmarkable view by name; query `colorMode`, `viewportWidth`, `layoutMode`, `reload` |
| `/render/export/pdf` | POST | Multi-page PDF download (`application/pdf`); see below |
| `/render/export/pdf/{renderId}` | GET | PDF from an existing **paginated** session render |
| `/render/lookupActions/` | POST | Hit-test interactions for coordinates (fallback; viewers prefer the bulk index) |
| `/render/info/interaction-regions/{renderId}/{pageNumber}` | GET | Prefetch all interactive hit regions for a page (geometry, context, actions) for client-side hover/click |
| `/render/getComponent/` | POST | Resolve component JSON at page coordinates (editor click) |

Example render body:

```json
{
  "presentationName": "list-presentations",
  "parameters": [],
  "reload": true,
  "colorMode": "light",
  "layoutMode": "continuous",
  "viewportWidth": 1280
}
```

**`reload`:** when `true`, any existing in-memory rendering for that presentation name (and parameter set) is **removed** before a new one is stored. Use this for a deliberate full refresh. The editor **View** toolbar action does **not** use `reload: true`; it reuses the edit session’s `renderId` so the Edit tab keeps working after you open a view in a new tab.

#### PDF export

Toolbar **PDF** opens a dialog (paper size, orientation, margins, **light/dark** — light is default for print). Continuous presentations are always **re-laid out as paginated** for the chosen paper; they are never exported as one tall web surface.

```json
POST /render/export/pdf
{
  "presentationName": "Maritime Executive Overview",
  "renderId": "<optional session id>",
  "useSessionLayout": true,
  "colorMode": "light",
  "paperPreset": "a4",
  "portrait": false,
  "margin": 25
}
```

`paperPreset`: `current` (session page size, paginated only), `a4`, `letter`, `legal`, `a3`, `custom` (+ `width`/`height`).

Example actions request:

```json
{
  "renderId": "811bedf3-8836-44dd-894e-7290850c52a7",
  "pageNumber": 0,
  "x": 123,
  "y": 456
}
```
