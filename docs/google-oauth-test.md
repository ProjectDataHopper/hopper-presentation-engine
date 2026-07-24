# Google OAuth test setup (local)

Test Hopper browser login with Google Cloud OAuth client **Data Hopper** (project `apachehop`).

## Prerequisites

1. OAuth client type: **Web application**
2. Authorized redirect URI: `http://localhost:8080/hopper/api/auth/callback`
3. Consent screen (Testing): test user `mattcasters@gmail.com`
4. Client secret available as env `GOOGLE_OAUTH_CLIENT_SECRET`

### Load secret from Google’s downloaded JSON

```bash
# One-time: write ~/.config/hopper/google-oauth.env (mode 600)
python3 - <<'PY'
import json, os
from pathlib import Path
# Adjust path if your download name differs
p = Path.home() / "Downloads"
files = list(p.glob("client_secret*27gng2hc0l4lri0rn3jgkjvurornp6iq*.json"))
if not files:
    raise SystemExit("client_secret JSON not found in ~/Downloads")
web = json.loads(files[0].read_text())["web"]
out = Path.home() / ".config/hopper/google-oauth.env"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(
    f"export GOOGLE_OAUTH_CLIENT_SECRET='{web['client_secret']}'\n"
)
os.chmod(out, 0o600)
print("Wrote", out)
PY

source ~/.config/hopper/google-oauth.env
test -n "$GOOGLE_OAUTH_CLIENT_SECRET" && echo "secret is set"
```

## Hopper config

Use gitignored directory `hopper-presentation-rest/config-google-local/` (Jetty) or `config-google-docker/` (Docker).

### Docker (recommended after image is published)

```bash
# Secret in env (from ~/.config/hopper/google-oauth.env)
source ~/.config/hopper/google-oauth.env

# From monorepo root — mounts Google OAuth properties + passes client secret
./run-docker-google.sh
```

Logs should show `Authentication enabled: mode=OAUTH2` (not DISABLED). Open  
http://localhost:8080/hopper/api/render/main/ — you should be redirected to Google login.

The script sets `HOPPER_REST_CONFIG_PATH=/hopper-data/config` and mounts  
`hopper-presentation-rest/config-google-docker/` (with `auth.enabled=true`, Google issuer, client id).  
`GOOGLE_OAUTH_CLIENT_SECRET` is passed into the container for `${GOOGLE_OAUTH_CLIENT_SECRET}` in properties.

**Important:** Config path is resolved from the **environment** first, then `-D` system properties.

Key settings:

| Property | Value |
|----------|--------|
| `auth.issuer-uri` | `https://accounts.google.com` |
| `auth.jwks-uri` | `https://www.googleapis.com/oauth2/v3/certs` |
| `auth.audience` | Google **client id** (not `hopper-presentation`) |
| `auth.username-claim` | `email` |
| `auth.roles-claim` | empty |
| `auth.default-roles` | `VIEWER` |
| `auth.admin-emails` | `mattcasters@gmail.com` → `ADMIN` |
| `auth.oidc.client-secret` | `${GOOGLE_OAUTH_CLIENT_SECRET}` or leave blank (env fallback) |

## Run

Easiest (handles Jetty module path):

```bash
./hopper-presentation-rest/scripts/run-google-oauth-local.sh
```

Manual equivalent — **Jetty is only on the REST module**, so do not use bare `jetty:run` from the parent POM (prefix resolution fails):

```bash
source ~/.config/hopper/google-oauth.env
export HOPPER_REST_CONFIG_PATH="$PWD/hopper-presentation-rest/config-google-local"

# From repo root: build deps, then run Jetty inside the REST module
mvn -pl hopper-presentation-rest -am install -DskipTests
cd hopper-presentation-rest
# Prefer -D as well as export (HRest reads both)
mvn jetty:run -DHOPPER_REST_CONFIG_PATH="$HOPPER_REST_CONFIG_PATH"
```

Alternatively from the root with a fully-qualified plugin goal:

```bash
mvn -pl hopper-presentation-rest -am \
  org.eclipse.jetty:jetty-maven-plugin:11.0.24:run \
  -DHOPPER_REST_CONFIG_PATH="$HOPPER_REST_CONFIG_PATH"
```

Open: http://localhost:8080/hopper/api/render/main/

You should be redirected to Google, then back with a session. Check:

```bash
curl -sS -c /tmp/h.jar -b /tmp/h.jar http://localhost:8080/hopper/api/auth/me
# after browser login, cookie is set; or use the UI
```

## Notes

- Google **access tokens** are usually opaque; Hopper uses the **id_token** JWT from the token response.
- Never commit `client_secret*.json` or `config-google-local/`.
