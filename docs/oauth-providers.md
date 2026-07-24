# OAuth / OIDC provider setup (admin API)

Hopper can be configured for popular OIDC backends via **provider presets** without hand-editing every `auth.*` property. Presets expand wizard fields into a settings patch that is stored as L1 runtime overrides (`server-settings` / `runtime`) and hot-applied.

All endpoints require `security.admin` (ADMIN).

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/oauth/presets` | List providers + wizard field schema |
| GET | `/api/admin/oauth/status` | Current auth mode, issuer, client id, secret configured (masked) |
| POST | `/api/admin/oauth/preview` | Expand inputs → settings map (no save) |
| POST | `/api/admin/oauth/test` | OIDC discovery + JWKS reachability |
| POST | `/api/admin/oauth/apply` | Persist + hot-reload (optional pre-test) |

### Request shape (preview / test / apply)

```json
{
  "provider": "google",
  "inputs": {
    "clientId": "….apps.googleusercontent.com",
    "clientSecretRef": "${GOOGLE_OAUTH_CLIENT_SECRET}",
    "redirectUri": "http://localhost:8080/hopper/api/auth/callback",
    "adminEmails": "you@example.com",
    "defaultRoles": "VIEWER"
  },
  "requireTest": true
}
```

- `clientSecretRef` should be an env reference (`${VAR}` or bare `VAR`). Raw secrets are discouraged and redacted on status/settings GET.
- `requireTest` (apply only, default `true`): run discovery before save; set `false` for offline / air-gapped apply.

## Providers

### Google (`google`)

| Field | Maps to |
|-------|---------|
| (fixed) | `auth.issuer-uri=https://accounts.google.com` |
| (fixed) | `auth.jwks-uri=https://www.googleapis.com/oauth2/v3/certs` |
| `clientId` | `auth.oidc.client-id`, `auth.audience` |
| `clientSecretRef` | `auth.oidc.client-secret` |
| `username` / email claims | `email` |
| `roles-claim` | empty (use default-roles + admin-emails + security-user) |

Also register redirect URI in Google Cloud Console. Prefer browser `id_token` (Hopper already does).

### Microsoft Entra ID (`entra`)

| Field | Maps to |
|-------|---------|
| `tenant` | issuer `https://login.microsoftonline.com/{tenant}/v2.0` |
| `clientId` | client id; default audience |
| `audience` | optional API app id |
| `rolesClaim` | default `roles` |

### Keycloak (`keycloak`)

| Field | Maps to |
|-------|---------|
| `baseUrl` + `realm` | `auth.issuer-uri={base}/realms/{realm}` |
| `rolesClaim` | default `realm_access.roles` |
| `audience` | default `hopper-presentation` |

### Okta (`okta`)

| Field | Maps to |
|-------|---------|
| `domain` + `authorizationServer` | `https://{domain}/oauth2/{as}` (default AS = `default`) |
| `rolesClaim` | default `groups` |

### Auth0 (`auth0`)

| Field | Maps to |
|-------|---------|
| `domain` | issuer `https://{domain}` |
| `rolesClaim` | default `https://hopper/roles` (configure an Action) |

### Generic OIDC (`generic`)

| Field | Maps to |
|-------|---------|
| `issuerUri` | `auth.issuer-uri` (+ discovery) |
| `jwksUri` | optional override |
| claim fields | username / email / roles |

## Example: Google local

```bash
# 1) Ensure secret is in the environment
export GOOGLE_OAUTH_CLIENT_SECRET='GOCSPX-…'

# 2) As ADMIN, apply preset (session cookie or static-dev)
curl -sS -X POST 'http://localhost:8080/hopper/api/admin/oauth/apply' \
  -H 'Content-Type: application/json' \
  -H 'Cookie: HOPPER_SESSION=…' \
  -d '{
    "provider": "google",
    "requireTest": true,
    "inputs": {
      "clientId": "1025587414122-….apps.googleusercontent.com",
      "clientSecretRef": "${GOOGLE_OAUTH_CLIENT_SECRET}",
      "redirectUri": "http://localhost:8080/hopper/api/auth/callback",
      "adminEmails": "you@gmail.com",
      "defaultRoles": "VIEWER"
    }
  }'
```

Then open `/hopper/api/auth/login` or the home page login flow.

## Related

- Layered settings: [security-and-audit.md](security-and-audit.md) (Admin settings)
- Manual Google walkthrough: [google-oauth-test.md](google-oauth-test.md)
