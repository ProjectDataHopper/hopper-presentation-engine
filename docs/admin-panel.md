# Administration panel

Browser UI for Hopper Presentation server administration (Jersey static resources + existing admin APIs).

## Open

```
http://localhost:8080/hopper/api/static/admin/
```

- **Auth enabled:** requires an authenticated principal with role `ADMIN` (`security.admin`).
- **Auth disabled:** panel is open for local demos (same as the rest of the API).

Also linked from:

- Home page → **Administration**
- User chip → **Admin** (when roles include ADMIN)
- Live usage page → **Administration**

## Tabs

| Tab | What you can do |
|-----|-----------------|
| **Overview** | Auth mode, issuer, sessions, renders, quick links |
| **Auth & OAuth** | Provider wizard (Google, Entra, Keycloak, Okta, Auth0, generic): Test → Preview → Apply |
| **Settings** | Edit effective `auth.*` / `audit.*` / server knobs; apply L1 overrides |
| **Roles** | View built-in matrix; create/edit/delete custom roles and action grants |
| **Users** | Assign Hopper roles to emails; disable users; see live sessions |
| **ACLs** | CRUD resource ACLs (role/user, ALLOW/DENY, action wildcards) |
| **Server** | Render cache TTL/size, housekeeping, force-evict; jump to audit settings |
| **Live usage** | Active renders and browser sessions |

## Server ops settings

| Key | Default | Effect |
|-----|---------|--------|
| `server.render.ttl-minutes` | 60 | Evict renderings idle longer than this |
| `server.render.max-entries` | 200 | LRU eviction when over capacity |
| `server.session.sweep-interval-seconds` | 60 | Housekeeping tick interval |

Change via **Settings** tab or `POST /api/admin/settings/apply`. Applied live (no restart).

Admin APIs:

- `GET /api/admin/server/status`
- `POST /api/admin/server/housekeeping/run`
- `DELETE /api/admin/server/renders` / `DELETE /api/admin/server/renders/{id}`

## Files

```
WEB-INF/static/admin/
  index.html
  admin.css
  admin.js
  pages/
    overview.js
    oauth.js
    settings.js
    roles.js
    users.js
    acls.js
    usage.js
```

Served via `StaticResourcesResource` (`static/admin/{path:.*}`).

## Secrets

The OAuth wizard stores **env references** only (e.g. `${GOOGLE_OAUTH_CLIENT_SECRET}`). Set the variable in the process environment before Apply / restart.

## First-admin bootstrap

1. Start with `auth.mode=static-dev` (or open API) so you can reach the panel, **or** use Google OAuth + `auth.admin-emails`.
2. Open `/hopper/api/static/admin/`.
3. **Auth & OAuth** → choose provider → Test → Apply (secret as `${ENV}`).
4. **Users** → assign roles; **Roles** → custom grants if needed.
5. **ACLs** → lock down sensitive presentations.
6. **Server** → set render TTL / max entries under Settings if desired.
7. Disable static-dev / open mode when ready.

## Related APIs

- [oauth-providers.md](oauth-providers.md)
- [security-and-audit.md](security-and-audit.md)
