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
| **Variables** | Define server-wide system variables inherited by presentations and connectors; encrypt secrets; delete rows |
| **Variable resolvers** | CRUD Hop `variable-resolver` metadata; type-specific forms from Hop `@GuiWidgetElement` plugins |
| **Connectors** | List/create/edit/delete data connectors (`connector` metadata); schema fields + plugin JSON |
| **Database connections** | CRUD + test for `hopper-database-connection` metadata |
| **Themes** | CRUD catalog themes (`theme` metadata); preserves colors/fonts on save |
| **Roles** | View built-in matrix; create/edit/delete custom roles and action grants |
| **Users** | Assign Hopper roles to emails; disable users; see live sessions |
| **ACLs** | CRUD resource ACLs (role/user, ALLOW/DENY, action wildcards) |
| **Server** | Render cache TTL/size, housekeeping, force-evict; jump to audit settings |
| **Live usage** | Active renders and browser sessions |

## Connectors, database connections, themes

Managed under **Connectors**, **Database connections**, and **Themes**. These tabs **host the same UIs** as the presentation editor toolbar (list chrome, connector studio with sample preview, DB test, theme form) by mounting `#editSidePanel` / `#editArea` and calling `editConnectorsList` / `editDatabaseConnectionsList` / `editThemesList` from `hopper-presentation.js`.

Persistence remains the process `JsonMetadataProvider` via `/api/metadata/…`:

| Metadata key | Admin tab | UI source |
|--------------|-----------|-----------|
| `connector` | Connectors | Presentation toolbar connector studio |
| `hopper-database-connection` | Database connections | Presentation toolbar DB admin |
| `theme` | Themes | Presentation toolbar theme admin |

Scripts (lazy-loaded on first open): `hopper-metadata-list.js`, `hopper-presentation.js`, `hopper-chain-edit.js`.

## System variables

Stored as Hop metadata `system-variables` / document `runtime`. Loaded at server start and applied to the shared `IVariables` space; every presentation data context copies these values first.

**Variable hierarchy at layout:** system variables → presentation parameter defaults (`HParameterDefinition`) → parameter mappings → request/interaction values (always win).

| API | Purpose |
|-----|---------|
| `GET /api/admin/variables` | List name/value pairs |
| `PUT /api/admin/variables` | Replace full set and apply live |
| `POST /api/admin/variables/encrypt` | `Encr.encryptPasswordIfNotUsingVariables(value)` |

In the UI, each row has a **lock** button (encrypt) and a **delete** icon. Values that already contain `${…}` or `#{…}` are left unencrypted by Hop.

Use as `${MY_VAR}` in connectors, SQL, REST paths, etc.

## Variable resolvers

Hop metadata type `variable-resolver`. Expressions: `#{resolverName:path:key}`.

Form fields for plugin-specific options are discovered from Hop `@GuiWidgetElement` annotations (e.g. Vault) and projected into Hopper’s `HGuiRegistry` / form schema — no need to re-annotate Hop plugins with `@HWidgetElement`.

| API | Purpose |
|-----|---------|
| `GET /api/admin/variable-resolvers/plugins` | Discovered `@VariableResolverPlugin` types |
| `GET /api/admin/variable-resolvers/schema/{pluginId}` | Form schema for plugin-specific fields |
| `GET /api/admin/variable-resolvers` | List names via `IHopMetadataProvider` |
| `GET /api/admin/variable-resolvers/{name}` | Load via serializer + form-flattened fields |
| `POST /api/admin/variable-resolvers/save` | Save via `JsonMetadataParser` + `IHopMetadataSerializer` |
| `POST /api/admin/variable-resolvers/test` | Resolve without saving |
| `DELETE /api/admin/variable-resolvers/{name}` | Delete via serializer |
| `GET/POST/DELETE /api/metadata/variable-resolver/…` | Same provider (generic metadata REST) |

Persistence always uses the process `JsonMetadataProvider` (`HRest.getMetadataProvider()`): form payload → Hop polymorphic JSON → `JsonMetadataParser.loadJsonObject` → `serializer.save` / `load` / `delete`.

**Classpath:** the REST WAR packages Hop resolver plugins:

- `hop-misc-passwords` — Hop Password Variable Resolver
- `hop-tech-vault` — Hashicorp Vault / OpenBao
- `hop-tech-google` — Google Secret Manager

Additional resolvers can still be added via the classpath.

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
    variables.js
    resolvers.js
    metadata-host.js
    connectors.js
    connections.js
    themes.js
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
