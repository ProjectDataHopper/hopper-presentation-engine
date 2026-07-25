# AGENTS.md — Hopper Presentation Engine

Guide for **AI agents** and **human developers** working in this repository.

This monorepo builds **metadata-driven, server-side SVG presentations** (reports and interactive dashboards) for the [Data Hopper](https://data-hopper.com) ecosystem. Data is acquired and drawn on the server; browsers display rendered SVG (and PDF), not raw row sets.

---

## Quick orientation

| Audience | Start here |
|----------|------------|
| **Agent generating presentations / connectors** | [`docs/ai/README.md`](docs/ai/README.md) → wire format → templates → validate |
| **Agent hardening or changing the engine** | This file + [`hopper-presentation-core/docs/architecture.md`](hopper-presentation-core/docs/architecture.md) |
| **Developer embedding or extending core** | [`hopper-presentation-core/README.md`](hopper-presentation-core/README.md) + [`docs/api.md`](hopper-presentation-core/docs/api.md) |
| **Developer running REST / editor** | [`hopper-presentation-rest/README.md`](hopper-presentation-rest/README.md) |

**Hard rule for agents:** never auto-save generated JSON into production metadata without human review. Always validate first.

---

## System requirements

| Requirement | Version / notes |
|-------------|-----------------|
| **Java** | **21** (`maven.compiler.release=21`) |
| **Maven** | **3.8+** |
| **Apache Hop** | **2.18.1** (`hop-core`, `hop-engine`, DB plugins as needed) |
| **Jandex** | **3.5.3** (must match Hop so annotation indexes are readable) |
| **Batik** | **1.19** (SVG 1.1; aligned with Hop) |
| **Jackson** | **2.18.2** |
| **Lombok** | **1.18.36** (prefer for new/refactored beans) |
| **JUnit** | **5.11.x** |
| **Jersey** (REST) | **3.1.9** |
| **Jetty** (local run) | **11.0.24** (plugin on the REST module only) |
| **OS** | Linux / macOS / Windows with a JDK 21 toolchain |

Artifacts:

- Parent: `org.hopper:hopper-presentation-engine:1.0.0-SNAPSHOT` (packaging `pom`)
- Core JAR: `org.hopper:hopper-presentation-core:1.0.0-SNAPSHOT`
- REST WAR: `org.hopper:hopper-presentation-rest:1.0.0-SNAPSHOT`

Nexus (snapshots/releases): `https://repository.data-hopper.com/repository/hopper/` — server id `hopper` in `~/.m2/settings.xml`. See [`hopper-presentation-core/docs/publishing.md`](hopper-presentation-core/docs/publishing.md).

---

## Project layout

```text
hopper-presentation-engine/          # Maven aggregator (this repo root)
├── AGENTS.md                        # This file
├── README.md                        # Human project overview
├── pom.xml                          # Shared versions, modules
├── docs/
│   ├── ai/                          # ★ Agent authoring entry point
│   │   ├── README.md                # Read-order + closed-loop validate/compile
│   │   ├── wire-format.md           # Hop polymorphic vs flat JSON
│   │   ├── overview.md              # Catalog, themes, mental model
│   │   ├── layout.md                # Attachments / place recipes
│   │   ├── connectors.md            # Connector plugin cheat sheet
│   │   ├── components.md            # Component plugin cheat sheet
│   │   ├── interactions.md          # Click targets / actions
│   │   ├── recipes/                 # End-to-end patterns (SQL→chart, params, …)
│   │   ├── templates/               # Golden JSON (copy and edit)
│   │   ├── schemas/                 # ★ JSON Schema (presentation, connector, plugins)
│   │   │   ├── presentation.schema.json
│   │   │   ├── connector.schema.json
│   │   │   └── plugins/{components,connectors}/*.schema.json
│   │   └── dsl/                     # Compact authoring DSL
│   ├── admin-panel.md
│   ├── security-and-audit.md
│   ├── platform-sso-docker.md
│   └── …
├── hopper-presentation-core/        # Library: model, plugins, layout, SVG, AI codec
│   ├── AGENTS.md                    # Short core-only notes (points here)
│   ├── docs/                        # Architecture, API, components, connectors
│   └── src/main/java/org/hopper/…
└── hopper-presentation-rest/        # WAR: REST API, canvas editor, admin, Jetty
    ├── config/                      # Default properties + sample metadata catalog
    ├── docs/smoke-test.md
    └── src/main/java/org/hopper/rest/…
```

### Module responsibilities

| Module | Role | Do / don't |
|--------|------|------------|
| **hopper-presentation-core** | Connectors, components, layout, SVG/PDF, metadata codec, validator, DSL, JSON Schema export | Prefer **server-side data + server-side SVG**. No browser-side data-fetch patterns. |
| **hopper-presentation-rest** | HTTP delivery, auth, editor/viewer JS, AI validate/compile endpoints, admin panel | All REST/HTTP concerns live here, not in core. |

### Naming conventions

- Java packages: `org.hopper.*`
- Domain types use a short **`H` prefix** (`HPresentation`, `HComponent`, `HConnector`, …)
- HTTP context: **`/hopper/`** — API base **`/hopper/api/`**
- Config file: `hopper-presentation.properties`
- Metadata type keys: `presentation`, `connector`, `theme`, `hopper-database-connection`, …

### Core package map (`org.hopper`)

| Package | Contents |
|---------|----------|
| `core` | `HEnvironment`, geometry, colors, `HJson`, GUI form schema, plugins util |
| `presentation` | `HPresentation`, pages, themes, parameters, layout |
| `presentation.component` / `.types.*` | Visual plugins (table, charts, label, …) |
| `presentation.connector` / `.types.*` | Data plugins (SQL, CSV, chain, filter, …) |
| `presentation.datacontext` | Variables, connector lookup, caching |
| `metadata.codec` | `HMetadataCodec` — dual wire-shape parse/serialize |
| `metadata.validate` | `HMetadataValidator`, CLI `HMetadataValidateMain` |
| `metadata.dsl` | `HAuthoringDsl` |
| `metadata.schema` | `HJsonSchemaExporter` |
| `render` | SVG/PDF helpers, render contexts |
| `security` / `audit` | RBAC, ACLs, usage lineage sinks |
| `config` | Server settings catalog, system variables |

REST lives under `org.hopper.rest` (resources, security filters, render cache, admin).

---

## Architecture (mental model)

```text
connector (data) ──► component (visual) ──► page ──► presentation
         ▲                    │
         └── sourceConnectorName / layout attachments
```

1. **Connectors** load or transform rows (SQL, CSV, sample, REST, sort/filter/chain, …). They are **catalog entries** under `connector/`, not embedded inside presentation files.
2. **Components** reference connectors by **name** (`sourceConnectorName`) and draw SVG.
3. **Presentations** compose pages, themes, interactions, and parameter mappings as Hop metadata JSON.
4. **Layout** uses relative **attachments** (page edge or peer component name + offset/percentage/alignment). The engine topologically sorts components.
5. **REST** serves render sessions (UUID render IDs), edit forms (annotation-driven), undo history, and the JS canvas editor.

Pipeline in code:

```text
HEnvironment.init()
  → HPresentation.doLayout(...)   // processSourceData + geometry + multi-page
  → HPresentation.render(...)     // SVG per HRenderPage
```

Deep dive: [`hopper-presentation-core/docs/architecture.md`](hopper-presentation-core/docs/architecture.md).

---

## For AI agents: authoring presentations & connectors

### Read order (mandatory)

1. [`docs/ai/README.md`](docs/ai/README.md) — closed loop and bootstrap APIs  
2. [`docs/ai/wire-format.md`](docs/ai/wire-format.md) — **Hop polymorphic vs flat** JSON  
3. [`docs/ai/overview.md`](docs/ai/overview.md) — catalog layout, themes, variables  
4. [`docs/ai/layout.md`](docs/ai/layout.md) — attachments and place recipes  
5. [`docs/ai/connectors.md`](docs/ai/connectors.md) / [`docs/ai/components.md`](docs/ai/components.md)  
6. [`docs/ai/interactions.md`](docs/ai/interactions.md) when adding clicks  
7. [`docs/ai/recipes/`](docs/ai/recipes/) and [`docs/ai/templates/`](docs/ai/templates/)  
8. [`docs/ai/dsl/`](docs/ai/dsl/) for compact generation  

Human depth remains under [`hopper-presentation-core/docs/`](hopper-presentation-core/docs/).

### JSON Schema (hard constraints)

Schemas under **`docs/ai/schemas/`** describe the **on-disk Hop file shape** used for validation and agent tooling:

| Schema | Purpose |
|--------|---------|
| `presentation.schema.json` | Presentation document (pages, components, interactions, parameters) |
| `connector.schema.json` | Connector catalog entry |
| `plugins/components/*.schema.json` | Per-component plugin bodies (e.g. `HTableComponent.schema.json`) |
| `plugins/connectors/*.schema.json` | Per-connector plugin bodies (e.g. `SqlConnector.schema.json`) |

**Also available live** when REST is running:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/hopper/api/ai/context` | Plugin list + doc pointers |
| `GET` | `/hopper/api/ai/schemas/presentation` | Presentation JSON Schema |
| `GET` | `/hopper/api/ai/schemas/connector` | Connector JSON Schema |
| `POST` | `/hopper/api/ai/validate/presentation` | Structural (+ optional `"smoke": true`) |
| `POST` | `/hopper/api/ai/validate/connector` | Structural |
| `POST` | `/hopper/api/ai/compile/presentation` | DSL → Hop JSON |
| `POST` | `/hopper/api/ai/compile/connector` | DSL → Hop JSON |

Schemas are generated/exported via `org.hopper.metadata.schema.HJsonSchemaExporter`. Prefer live endpoints when the server is up so schemas stay in sync with the running classpath.

### Wire format rules agents must follow

**Canonical on-disk shape** (Hop `JsonMetadataParser` polymorphic wrapper):

```json
{
  "name": "runs",
  "layout": {
    "left": { "offset": 12, "percentage": 0, "alignment": "LEFT" },
    "top":  { "offset": 48, "percentage": 0, "alignment": "TOP" }
  },
  "component": {
    "HTableComponent": {
      "pluginId": "HTableComponent",
      "sourceConnectorName": "ops-runs-sample",
      "header": true
    }
  }
}
```

Connector:

```json
{
  "name": "ops-runs-sample",
  "shared": true,
  "connector": {
    "CsvConnector": {
      "pluginId": "CsvConnector",
      "filename": "${HOPPER_METADATA_PATH}/sample/ops-runs.csv",
      "headerPresent": true,
      "separator": ",",
      "fields": [{ "name": "runId", "type": "String" }]
    }
  }
}
```

**Flat Jackson shape** (`pluginId` at the plugin object root) is also accepted by `HMetadataCodec` / tests. Prefer **canonical Hop shape** for files destined for `metadata/` folders.

Parser entry points:

- `HMetadataCodec.parsePresentation(json)` / `parseConnector(json)`
- `HMetadataCodec.toHopJson(...)` for disk-compatible output

### Closed-loop generation workflow

```text
1. Copy a golden template from docs/ai/templates/  (or draft DSL)
2. Adjust names, connectors, place/layout, columns
3. Validate:
     POST /hopper/api/ai/validate/presentation  { "json": "...", "smoke": true }
   or CLI (core classpath):
     java ... org.hopper.metadata.validate.HMetadataValidateMain \
       --type presentation --file my.json [--catalog metadata_dir] [--smoke]
4. Fix by validation codes (ATTACHMENT_MISSING, UNKNOWN_PLUGIN, …)
5. Human review → save via metadata APIs or files under metadata.path
```

DSL path (compact authoring):

```text
POST /hopper/api/ai/compile/presentation  { "dsl": "{ ... }" }
  → Hop JSON + validation report
```

DSL is **not bidirectional**. See [`docs/ai/dsl/README.md`](docs/ai/dsl/README.md).

### Quality checklist for generated output

- [ ] **Stable unique component names** on each page (`title`, `runs-table`); attachments reference those names.
- [ ] **Connector names** match catalog keys exactly (`sourceConnectorName`).
- [ ] **No invented plugin ids** — use cheat sheets, schemas, or `GET /ai/context`.
- [ ] Prefer **templates + place recipes** (`topLeft`, `under`, `belowFill`, …) over inventing attachment math.
- [ ] Keep JSON **minimal** (omit nulls / default noise).
- [ ] For browser dashboards, set **`layoutMode": "continuous"`** (and optional **`designWidth`**); leave **`paginated`** for print-style multi-page sheets.
- [ ] Labels are **single-line**; multi-line notes → `HTextBlockComponent`.
- [ ] SQL connectors need a matching **`hopper-database-connection`** by name.
- [ ] Paths use variables: `${HOPPER_METADATA_PATH}`, `${HOPPER_DATA_PATH}`, `${PARAM}`.
- [ ] Charts need meaningful dimension/fact config.
- [ ] SVG artwork: **SVG 1.1** only; Batik does not support SVG2-only filters such as `feDropShadow`.
- [ ] Validate (and ideally smoke-render) before hand-off.
- [ ] **Never auto-save** to production metadata.

### Golden templates & recipes

| Path | Use when |
|------|----------|
| `docs/ai/templates/presentation-title-table.json` | Title + table layout |
| `docs/ai/templates/presentation-charts-row.json` | Chart row layouts |
| `docs/ai/templates/presentation-text-block.json` | Multi-line notes |
| `docs/ai/templates/connector-csv.json` | CSV source |
| `docs/ai/templates/connector-sql.json` | SQL source |
| `docs/ai/recipes/sql-to-bar-chart.md` | SQL → bar chart |
| `docs/ai/recipes/csv-to-table.md` | CSV → table |
| `docs/ai/recipes/chain-filter-sort.md` | Transform chains |
| `docs/ai/recipes/params-and-labels.md` | Parameters / variables / interactions |
| `docs/ai/recipes/multi-line-notes.md` | Text blocks |

Sample live catalog (good real-world JSON):  
`hopper-presentation-rest/config/metadata/{presentation,connector,theme,sample}/`.

### Built-in plugins (short lists)

**Components** (plugin id):  
`HLabelComponent`, `HTextBlockComponent`, `HTableComponent`, `HBarChartComponent`, `HLineChartComponent`, `HPieChartComponent`, `HGanttChartComponent`, `HCrosstabComponent`, `HImageComponent`, `HSvgComponent`, `HGroupComponent`, `HCompositeComponent`.

**Connectors** (plugin id):  
`CsvConnector`, `SqlConnector`, `SampleDataConnector`, `HRestConnector`, `BinaryRowsConnector`, `SortConnector`, `SimpleFilterConnector`, `DistinctConnector`, `SelectionConnector`, `AggregateConnector`, `ChainConnector`, `PassthroughConnector`, `HListConnector`, `MetadataTypesConnector`, `MetadataElementsConnector`, `MetadataPresentationsConnector`.

Full field detail: core docs + per-plugin schemas under `docs/ai/schemas/plugins/`.

### Metadata catalog layout

Under `metadata.path` (see `hopper-presentation.properties`):

| Folder | Type key |
|--------|----------|
| `presentation/` | `presentation` |
| `connector/` | `connector` |
| `theme/` | `theme` |
| `hopper-database-connection/` | `hopper-database-connection` |

Variables commonly used in JSON:

| Variable | Meaning |
|----------|---------|
| `${PARAM}` | Presentation / request parameter |
| `${HOPPER_METADATA_PATH}` | Metadata root |
| `${HOPPER_DATA_PATH}` | Runtime data (cache, timings) |

Parameter precedence at layout (later wins): system variables → presentation defaults → parameter mappings → request/interaction parameters. Details: [`hopper-presentation-core/docs/api.md`](hopper-presentation-core/docs/api.md), recipe [`params-and-labels.md`](docs/ai/recipes/params-and-labels.md).

---

## For developers: build, run, and code

### Build

From the **repository root**:

```bash
mvn clean install
```

Core only:

```bash
mvn -pl hopper-presentation-core -am clean verify
```

Notes:

- Surefire runs **sequentially** (`forkCount=1`, `parallel=none`). Hop `PluginRegistry` / `JarCache` are not safe for concurrent multi-class initialization.
- Jandex is built into the core JAR so Hop can discover `@HComponentPlugin` / `@HConnectorPlugin` / `@HopMetadata` types.

### Run REST + editor (local)

The parent POM has **no** Jetty plugin. Use the REST module:

```bash
mvn -pl hopper-presentation-rest -am install -DskipTests
cd hopper-presentation-rest && mvn jetty:run
```

Or from root with fully-qualified goal:

```bash
mvn -pl hopper-presentation-rest -am org.eclipse.jetty:jetty-maven-plugin:11.0.24:run
```

Point `metadata.path` at a folder with `presentation/`, `connector/`, `theme/`, `hopper-database-connection/` (see `hopper-presentation-rest/config/hopper-presentation.properties`).

Main UI: `http://localhost:8080/hopper/api/render/main/`

Smoke test: [`hopper-presentation-rest/docs/smoke-test.md`](hopper-presentation-rest/docs/smoke-test.md).

Docker / Keycloak fleet SSO: [`docs/platform-sso-docker.md`](docs/platform-sso-docker.md), scripts `run-docker.sh` / `run-docker-google.sh`.

### Embed core in another app

```java
HEnvironment.init();  // once per JVM, idempotent

IHopMetadataProvider metadata = new MemoryMetadataProvider();
HPresentation presentation = HPresentation.fromJsonString(json);

HLayoutResults layout =
    presentation.doLayout(parentLog, new PresentationRenderContext(presentation, metadata),
        metadata, List.of());

presentation.render(layout, metadata);
String svg = layout.getRenderPages().get(0).getSvgXml();
```

Use `HJson.createMapper()` for presentation JSON round-trips that ignore Hop runtime fields.

### Coding conventions

1. **Java 21**; keep Hop / Jandex / Batik versions aligned with the parent POM.
2. Prefer **Lombok** (`@Getter` / `@Setter` / etc.) for new and refactored beans.
3. Domain types: **`H` prefix**; plugins annotated with `@HComponentPlugin` / `@HConnectorPlugin` / `@HopMetadata` + `@HopMetadataProperty`.
4. Editor form fields: use existing GUI annotations / `HWidgetElement` patterns so REST can render forms (`GuiFormSchemaBuilder`).
5. **Server-side only** for data and drawing; do not introduce browser-side DB/API data paths in the engine.
6. REST/HTTP belongs in **hopper-presentation-rest**, not core.
7. Call **`HEnvironment.init()`** once before plugins/metadata use.
8. Prefer Apache Hop VFS patterns where the Hop ecosystem already does for I/O (connectors that read files).
9. New source files intended for ASF-style publishing: Apache License 2.0 header.
10. SVG assets: SVG 1.1 primitives; avoid SVG2-only filters (`feDropShadow` → compose `feGaussianBlur` + `feOffset` + `feFlood` + `feMerge`).

### Adding a component or connector plugin

1. Implement under `presentation.component.types.*` or `presentation.connector.types.*`.
2. Annotate with `@HComponentPlugin` / `@HConnectorPlugin` (and Hop metadata properties for serializable fields).
3. Extend `HBaseComponent` / `HBaseConnector` (or caching base where appropriate).
4. Register via Jandex (built automatically); ensure `HEnvironment.init()` discovers it.
5. Add unit tests under `src/test/java`.
6. Update agent-facing docs:
   - `docs/ai/components.md` or `connectors.md`
   - plugin schema under `docs/ai/schemas/plugins/…` (or regenerate via exporter / REST schema endpoint)
   - optionally a template or recipe
7. Deep human docs: `hopper-presentation-core/docs/components.md` or `connectors.md`.

### Tests

```bash
mvn -pl hopper-presentation-core test
mvn -pl hopper-presentation-rest test
```

- Core tests include metadata codec, DSL, validator, component/connector behaviour, layout.
- REST tests cover resources and sample metadata under `hopper-presentation-rest/src/test/resources`.
- Keep tests sequential-friendly; avoid parallel suite assumptions around Hop statics.

### Auth, security, admin (REST)

Defaults are **open** for local demos. Production-style options:

- `auth.mode=static-dev` or `oauth2` (JWT / OIDC PKCE)
- Roles: `VIEWER`, `AUTHOR`, `DATA_ENGINEER`, `ADMIN`, `AUDITOR`
- Optional resource ACLs (`security-acl`)
- Admin panel: `/hopper/api/static/admin/`
- Audit sinks: logging / JSONL / plugins

See [`docs/security-and-audit.md`](docs/security-and-audit.md) and [`docs/admin-panel.md`](docs/admin-panel.md).

### Editor / render session notes

- `POST /render/presentation` returns a **render UUID** (in-memory session).
- Edit → View should reuse the current `renderId` (do not discard the edit session).
- Avoid `reload: true` on paths that would drop the editor’s cached rendering.
- Soft re-render after save: `POST …/edit/presentation/{name}/render/`.

Details: root [`README.md`](README.md) and [`hopper-presentation-rest/README.md`](hopper-presentation-rest/README.md).

---

## Documentation map

| Path | Audience | Content |
|------|----------|---------|
| [`docs/ai/`](docs/ai/README.md) | Agents | Authoring, schemas, templates, DSL, validate loop |
| [`docs/ai/schemas/`](docs/ai/schemas/) | Agents | JSON Schema artifacts |
| [`hopper-presentation-core/docs/architecture.md`](hopper-presentation-core/docs/architecture.md) | Both | Layout pipeline, streaming contract, plugins |
| [`hopper-presentation-core/docs/api.md`](hopper-presentation-core/docs/api.md) | Devs | Embed API, parameters, AI entry points |
| [`hopper-presentation-core/docs/components.md`](hopper-presentation-core/docs/components.md) | Both | Component behaviour |
| [`hopper-presentation-core/docs/connectors.md`](hopper-presentation-core/docs/connectors.md) | Both | Connector behaviour |
| [`hopper-presentation-core/docs/ecosystem.md`](hopper-presentation-core/docs/ecosystem.md) | Devs | Related repos |
| [`hopper-presentation-core/docs/publishing.md`](hopper-presentation-core/docs/publishing.md) | Devs | Nexus deploy |
| [`hopper-presentation-rest/docs/smoke-test.md`](hopper-presentation-rest/docs/smoke-test.md) | Devs | E2E render checklist |
| [`docs/security-and-audit.md`](docs/security-and-audit.md) | Devs | Auth, RBAC, audit |
| [`docs/platform-sso-docker.md`](docs/platform-sso-docker.md) | Devs | Fleet Docker / Keycloak |
| [`docs/ui-theming.md`](docs/ui-theming.md) | Both | Themes / UI tokens |

---

## Related ecosystem

| Project | Role |
|---------|------|
| **hopper-presentation-core** (this monorepo) | Core library |
| **hopper-presentation-rest** (this monorepo) | REST + editor |
| **hopper-hop-plugins** | Hop pipeline connector + pipeline/workflow diagram components |
| **hop-hopper-plugins** | Hop GUI AutoDoc |
| **hopper-swt-viewer** | Desktop SWT viewer |
| **hopper-viewer** | Deprecated Jetty viewer → use REST |
| **hopper-frontend** | Archived Vaadin UI |

Dependency direction: REST / viewers / Hop plugins → **core** → Hop + Batik + Jackson.

---

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
