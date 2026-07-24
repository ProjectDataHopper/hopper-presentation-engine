# Security and usage audit

Hopper Presentation Engine is designed so that **who** is using the system is known at all times, and **what** they did is exportable with full presentation-layer lineage — from the presentation and parameters down to the connector, connection, statement fingerprint, row counts, and timings.

This document describes the architecture introduced for authentication, action-based authorization, and pluggable audit sinks. Implementation is phased; see the status table below.

## Goals

1. **Authentication** — OAuth2/OIDC resource server (JWT). External IdP owns passwords; Hopper validates tokens.
2. **Authorization** — roles + **action** catalog (`presentation.render`, `connector.create`, …) with optional resource ACLs.
3. **Usage audit** — structured events with design + execution lineage, shipped via **audit sink plugins** (log, JSONL, Kafka, OpenSearch, …).

## Status

| Phase | Capability | Status |
|-------|------------|--------|
| 0 | Domain models, action catalog, audit plugin SPI | **Done** |
| 1 | `HAuthorizationService`, static-dev principal, REST enforcement | **Done** |
| 2 | OAuth2 JWT resource server | **Done** |
| 3 | Execution lineage (`HExecutionTrace`) on layout/connectors | **Done** |
| 4 | Async `HAuditEmitter` + Logging/JSONL sinks | **Done** |
| 5 | Resource ACLs | **Done** |
| 6 | Kafka / OpenSearch plugin modules | Planned |
| 7 | Browser OIDC login + live usage admin | **Done** |
| 8a | Admin settings foundation (schema, L1 overrides, API) | **Done** |
| 8b | Roles / users / ACL admin APIs + principal enrichment | **Done** |
| 8c | OAuth provider presets + test/apply API | **Done** |
| 8d | Admin HTML UI | **Done** |
| 8e | Server ops (render TTL/LRU, housekeeping) | **Done** |
| 9a | Platform SSO contract (role aliases, Keycloak preset, health, Docker) | **Done** |
| 9b | Ops dashboards (Ship REST + Bearer, seed pack, auto-refresh) | **Done** |

## Architecture

```
Client (Bearer JWT / static-dev)
        │
        ▼
REST filters → HSecurityContext (ThreadLocal HPrincipal)
        │
        ├─ HAuthorizationService.can(principal, action, resource?)
        │
        ▼
Core layout / connectors / metadata APIs
        │
        ▼
HAuditEmitter → IAuditSink plugins (log, file, Kafka, OpenSearch, …)
```

- **Core** owns principal context, authorization checks, audit models, and sink SPI (usable from REST, SWT, Hop hosts).
- **REST** owns token validation, HTTP path → action mapping, request correlation ids.
- **`HUserHistory`** remains “recent objects” UX only — not the compliance audit trail.

## Authentication

### Modes (`auth.mode`)

| Mode | Purpose |
|------|---------|
| `disabled` | Default. Open API (current behavior). Loud startup warning if used outside tests. |
| `static-dev` | Fixed user/roles from properties, or optional `X-Hopper-User` / `X-Hopper-Roles` headers. CI and local only. |
| `oauth2` | Validate JWT Bearer access tokens (issuer / JWKS / audience / claims → roles). |

### Properties

```properties
auth.enabled=false
auth.mode=disabled

# static-dev
auth.dev.user=admin
auth.dev.roles=ADMIN
auth.dev.allow-header-override=true

# oauth2 resource server
auth.mode=oauth2
auth.issuer-uri=https://idp.example.com/realms/hopper
auth.jwks-uri=                         # optional; discovered via OIDC if blank
auth.audience=hopper-presentation
auth.username-claim=preferred_username
auth.email-claim=email
auth.roles-claim=realm_access.roles    # nested path supported (Keycloak)
auth.roles-claim-prefix=               # e.g. hopper_ → strip before role match
auth.required-scopes=                  # optional space-separated
auth.clock-skew-seconds=60
auth.jwks.connect-timeout-ms=5000
auth.jwks.read-timeout-ms=5000
# Dev/test only — HS256 shared secret when no JWKS/issuer (never in production)
auth.jwt.hmac-secret=
```

When `auth.enabled=false`, filters do not require a principal (backward compatible).

### OAuth2 resource server behaviour

1. Client sends `Authorization: Bearer <access_token>`.
2. `AuthenticationFilter` validates the JWT via Nimbus (`OAuth2JwtValidator`):
   - Signature via JWKS (`auth.jwks-uri`, or OIDC discovery from `auth.issuer-uri`)
   - Claims: `exp`, optional `iss`, optional `aud`, required `sub`
   - Clock skew from `auth.clock-skew-seconds`
3. Claims map to `HPrincipal` (`JwtClaimMapper`): username, email, roles (with optional prefix strip; simple role names uppercased to match `VIEWER` / `AUTHOR` / …).
4. Failures → **401** + `WWW-Authenticate: Bearer …` and an `AUTH_FAILURE` audit event (no token body logged).
5. Static assets under `static/` and `OPTIONS` remain exempt.

**JWKS resolution order:** injected source (tests) → `auth.jwks-uri` → OIDC discovery `{issuer}/.well-known/openid-configuration` → `auth.jwt.hmac-secret` (dev only).

### Keycloak setup (example)

1. Create realm (e.g. `hopper`) and a **confidential** or **public** client for your API consumers.
2. Create client roles or realm roles matching Hopper roles: `VIEWER`, `AUTHOR`, `DATA_ENGINEER`, `ADMIN`, `AUDITOR` (or use `auth.roles-claim-prefix` if you prefer `hopper_viewer`).
3. Map roles into the access token (`realm_access.roles` is the Keycloak default).
4. Ensure the access token **audience** includes `hopper-presentation` (Keycloak: client scope *Audience* mapper, or set `auth.audience` to your client id if that is what appears in `aud`).
5. Configure Hopper:

```properties
auth.enabled=true
auth.mode=oauth2
auth.issuer-uri=https://keycloak.example.com/realms/hopper
# optional explicit JWKS:
# auth.jwks-uri=https://keycloak.example.com/realms/hopper/protocol/openid-connect/certs
auth.audience=hopper-presentation
auth.username-claim=preferred_username
auth.roles-claim=realm_access.roles
```

6. Call the API:

```bash
TOKEN=$(curl -s -X POST "$KEYCLOAK/realms/hopper/protocol/openid-connect/token" \
  -d "client_id=..." -d "username=alice" -d "password=..." -d "grant_type=password" \
  | jq -r .access_token)

curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/hopper/api/metadata/presentations/
```

Invalid or missing token → HTTP 401. Valid token with only `VIEWER` → mutations return HTTP 403.

### Google Cloud OIDC (local test)

See **[google-oauth-test.md](google-oauth-test.md)** for Console steps, secret env setup, and jetty runbook. Hopper prefers the OIDC **`id_token`** after PKCE (Google access tokens are usually opaque).

### Browser login (Phase 7 — OIDC PKCE)

API clients keep using Bearer tokens. The **canvas editor / home page** use Authorization Code + PKCE and an HttpOnly session cookie.

```properties
auth.enabled=true
auth.mode=oauth2
auth.issuer-uri=https://keycloak.example.com/realms/hopper
auth.audience=hopper-presentation
auth.oidc.client-id=hopper-ui
# auth.oidc.client-secret=   # optional (confidential client)
auth.oidc.redirect-uri=http://localhost:8080/hopper/api/auth/callback
auth.oidc.scopes=openid profile email
auth.session.cookie-name=HOPPER_SESSION
auth.session.ttl-minutes=480
auth.session.cookie-secure=false
```

| Endpoint | Purpose |
|----------|---------|
| `GET /api/auth/config` | Public: auth enabled? browser login available? |
| `GET /api/auth/login?returnTo=` | Start PKCE (or static-dev session) |
| `GET /api/auth/callback` | OIDC redirect handler → set cookie |
| `GET /api/auth/me` | Current principal JSON |
| `POST /api/auth/logout` | Clear session cookie |

Static assets include `hopper-auth.js` (401 → login redirect, user chip). Home page link **Live usage** opens the admin view.

### Live usage admin (Phase 7)

| Endpoint | Who | Purpose |
|----------|-----|---------|
| `GET /api/admin/usage/active` | ADMIN / AUDITOR | Active in-memory renders |
| `GET /api/admin/usage/sessions` | ADMIN / AUDITOR | Browser sessions |
| `/api/static/admin-usage.html` | Browser UI | Polling table |

Renders are registered when stored in the render cache and removed when discarded.

Presentation open/render also updates `UserHistoryUtil` under the **current principal username** (UX recent-items, not compliance audit).

## Authorization

### Actions

Stable string codes (also as `HAction` enum). Examples:

| Action | Meaning |
|--------|---------|
| `presentation.list` / `.read` / `.create` / `.update` / `.delete` | Presentation metadata |
| `presentation.render` / `.export` | Execute data + layout / export |
| `connector.list` / `.read` / `.create` / `.update` / `.delete` / `.preview` | Connectors |
| `component.create` / `.update` / `.delete` | Components on a presentation |
| `connection.*` / `connection.use` | DB connection metadata / use at query time |
| `theme.*` | Themes |
| `security.admin` / `audit.read` / `metadata.admin` | Admin |

### Built-in roles

| Role | Summary |
|------|---------|
| `VIEWER` | List/read/render/export presentations; no mutations |
| `AUTHOR` | VIEWER + presentation/component/theme CRUD + connector preview |
| `DATA_ENGINEER` | AUTHOR + connector + connection CRUD |
| `ADMIN` | All actions |
| `AUDITOR` | Read-only + `audit.read` |

Roles arrive from IdP claims (OAuth2) or static-dev configuration. Local password stores are out of scope for v1.

### Resource ACLs (Phase 5)

Per-resource access control is stored as Hop metadata key **`security-acl`**.

Document name convention: `{RESOURCE_TYPE}:{resourceName}` (e.g. `PRESENTATION:HR Salary`).

```json
{
  "name": "PRESENTATION:HR Salary",
  "resourceType": "PRESENTATION",
  "resourceName": "HR Salary",
  "entries": [
    {
      "principalType": "ROLE",
      "principal": "VIEWER",
      "actions": ["presentation.render", "presentation.read"],
      "effect": "DENY"
    },
    {
      "principalType": "USER",
      "principal": "alice",
      "actions": ["presentation.*"],
      "effect": "ALLOW"
    }
  ]
}
```

**Evaluation order**

1. Anonymous → deny  
2. System / `ADMIN` → allow  
3. Matching ACL **DENY** → deny  
4. Matching ACL **ALLOW** → allow (can grant beyond global role)  
5. If `authz.default-deny-resources=true` → deny (must have explicit ALLOW)  
6. Else fall back to global role → action matrix  

**Enforcement points**

| Surface | Check |
|---------|--------|
| `POST /render/presentation` | `presentation.render` on named presentation |
| `edit/presentation/{name}/**`, metadata by name | action + resource from path |
| `HSqlConnector` stream/describe | `connection.use` on DB connection name |

**Admin API** (requires `security.admin`):

| Method | Path |
|--------|------|
| GET | `/api/security/acls/` |
| GET | `/api/security/acls/{name}` |
| POST | `/api/security/acls/` |
| DELETE | `/api/security/acls/{name}` |

```properties
authz.default-deny-resources=false
```

## Audit / lineage

### Event types (selected)

`PRESENTATION_RENDER`, `CONNECTOR_PREVIEW`, `CONNECTOR_EXECUTE`, `METADATA_*`, `AUTHZ_DENY`, `AUTH_FAILURE`, `INTERACTION`, `EXPORT`, `SECURITY_CHANGE`.

### Execution lineage (Phase 3)

- `HExecutionTrace` attaches to `IDataContext` during layout/preview.
- Every connector `startStreaming` is wrapped in `HBaseConnector` and records a `HConnectorRun` (plugin id, source connector, row count, duration, outcome).
- SQL connectors add connection name, statement text (capped), and `sha256:` fingerprint.
- CSV/REST add filename/URL attributes.
- `RenderFactory` emits `PRESENTATION_RENDER` with design snapshot (presentation, components, parameters) + execution map; connector studio emits `CONNECTOR_PREVIEW`.
- Metadata save/delete emits `METADATA_CREATE` / `UPDATE` / `DELETE`.

### Payload principles

- Always: actor, action, resource names, timings, connector plugin ids, connection names, row counts, correlation (`requestId`, `renderId`).
- Optional / redacted: parameter values, full SQL text, row samples (default **off**).
- `schemaVersion: 1` for additive JSON stability.

### Sink SPI

```java
@HAuditPlugin(id = "LoggingAuditSink", name = "Logging", description = "...")
public class LoggingAuditSink implements IAuditSink { ... }
```

Registered via Hop `PluginRegistry` as `HAuditPluginType` (same pattern as connectors/components).

Built-in sinks:

| Plugin id | Purpose |
|-----------|---------|
| `LoggingAuditSink` | JDK log summary (or `format=json`) |
| `JsonlFileAuditSink` | Append JSON Lines via HopVFS (`path=…`) |

### Async emitter + configuration

```properties
audit.enabled=true
audit.fail-open=true
audit.async=true
audit.queue.size=10000
audit.queue.full-policy=drop   # or block
audit.redact.parameter-values=false
audit.redact.parameter-names=ssn,email,password,secret
audit.include.sql-text=true
audit.max-statement-length=4000
audit.bootstrap.logging=true
audit.bootstrap.jsonl.path=/var/log/hopper/audit.jsonl
```

`HAuditSinkLoader.bootstrap()` applies config, registers bootstrap sinks, then loads all enabled `audit-sink` metadata entries.

### Metadata type `audit-sink`

```json
{
  "name": "prod-jsonl",
  "pluginId": "JsonlFileAuditSink",
  "enabled": true,
  "eventTypes": ["PRESENTATION_RENDER", "METADATA_DELETE", "AUTHZ_DENY"],
  "properties": [
    { "name": "path", "value": "/var/log/hopper/audit.jsonl" },
    { "name": "append", "value": "true" }
  ]
}
```

Empty `eventTypes` = all events. Heavy clients (Kafka, OpenSearch) will be optional modules (Phase 6).

### Fail-open

By default, sink failures never break render or save (`audit.fail-open=true`). Full async queue drops events when full unless `audit.queue.full-policy=block`.

## REST enforcement (Phase 1)

When auth is enabled:

1. Authentication filter establishes `HPrincipal` on `HSecurityContext`.
2. Authorization filter maps HTTP method + path to `HAction` and calls `HAuthorizationService`.
3. Response filter clears the context and echoes `X-Request-Id`.

Exempt when auth enabled: `OPTIONS`, static assets under `static/`.

## Related code

| Package / type | Role |
|----------------|------|
| `org.hopper.security.*` | Principal, actions, roles, authorization |
| `org.hopper.audit.*` | Events, emitter, sink SPI |
| `org.hopper.rest.security.*` | Jersey filters, path→action map |
| `org.hopper.core.history.HUserHistory` | UX recent-items only |

## Admin settings (runtime configuration)

Operators can inspect and change most `hopper-presentation.properties` keys **without editing the file**, via layered settings:

| Layer | Source | Writable from API? |
|-------|--------|--------------------|
| Defaults | Built-in `HSettingsCatalog` | No |
| Bootstrap (L0) | `hopper-presentation.properties` | No (read-only in admin) |
| Overrides (L1) | Hop metadata `server-settings` / document `runtime` | **Yes** |
| Secrets (L2) | Environment variables; store `${ENV_NAME}` refs only | Reference only |

### Admin API (requires `security.admin`)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/settings` | Effective values (secrets redacted; env refs shown) |
| GET | `/api/admin/settings/schema` | Full schema for forms |
| POST | `/api/admin/settings/apply` | Patch body `{"settings":{"auth.mode":"oauth2",...}}` |

Apply validates types/enums, persists L1 overrides, and hot-reloads security/session/CORS where possible. Keys marked `restartRequired` (e.g. `audit.queue.size`, `metadata.path`) are reported but not fully applied until restart. `metadata.path` is read-only.

### Example apply

```http
POST /hopper/api/admin/settings/apply
Content-Type: application/json

{
  "settings": {
    "auth.enabled": "true",
    "auth.mode": "oauth2",
    "auth.issuer-uri": "https://accounts.google.com",
    "auth.oidc.client-id": "….apps.googleusercontent.com",
    "auth.oidc.client-secret": "${GOOGLE_OAUTH_CLIENT_SECRET}",
    "auth.default-roles": "VIEWER",
    "auth.admin-emails": "admin@example.com"
  }
}
```

### Roles and user assignments

| Concept | Storage | Notes |
|---------|---------|--------|
| Built-in roles | `HRole` / `HBuiltInRoles` | Immutable grants; cannot delete via API |
| Custom roles | Metadata `security-role` | Action codes, optional `inheritsFrom`, wildcards (`presentation.*`, `*`) |
| User assignments | Metadata `security-user` | Additive roles by email/subject; `disabled` strips roles |
| Resource ACLs | Metadata `security-acl` | Existing API `/api/security/acls` |

Principal enrichment runs on every authenticated request (Bearer or session): IdP roles ∪ default-roles ∪ admin-emails ∪ `security-user.roles`.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/roles` | System + custom roles |
| GET | `/api/admin/roles/actions` | `HAction` catalog for UI |
| GET | `/api/admin/roles/{name}` | Role detail (expanded actions for custom) |
| POST | `/api/admin/roles` | Create/update custom role |
| DELETE | `/api/admin/roles/{name}` | Delete custom role only |
| GET | `/api/admin/users` | Assignments + observed session users |
| GET/POST/DELETE | `/api/admin/users[/{name}]` | User assignment CRUD |
| POST | `/api/admin/users/{name}/roles` | Patch roles only |

OAuth provider wizards (API): see [oauth-providers.md](oauth-providers.md).

### Admin HTML UI

Open **`/hopper/api/static/admin/`** (requires ADMIN when auth is enabled; open when auth is disabled for local demos).

| Tab | Backed by |
|-----|-----------|
| Overview | status + usage + counts |
| Auth & OAuth | `/api/admin/oauth/*` |
| Settings | `/api/admin/settings` |
| Roles | `/api/admin/roles` |
| Users | `/api/admin/users` |
| ACLs | `/api/security/acls` |
| Live usage | `/api/admin/usage/*` |
| Server | `/api/admin/server/*` (cache stats, evict, housekeeping) |

Home page and the user chip (ADMIN only) link to the panel.

## Non-goals (v1)

- Replacing enterprise IdPs
- SQL rewrite / column-level security
- Persisting full result sets in audit
- Merging compliance audit into `HUserHistory`
