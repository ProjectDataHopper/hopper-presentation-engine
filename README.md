# Hopper Presentation Engine

![Hopper Presentation](assets/icons/hopper-presentation.svg)

**Metadata-driven presentations and interactive dashboards** for the [Data Hopper](https://data-hopper.com) ecosystem.

This project is the presentation engine behind server-side SVG reporting, data connectors, and a browser canvas editor. It is built to grow into simple interactive, dashboard-style UIs on top of the same metadata model.

## Modules

| Module | Artifact | Role |
|--------|----------|------|
| `hopper-presentation-core` | JAR | Rendering, connectors, components, themes |
| `hopper-presentation-rest` | WAR | REST API + canvas editor / viewer |

## Naming

- Java packages: `org.hopper.*`
- Domain types use a short **`H` prefix** (e.g. `HPresentation`, `HComponent`, `HConnector`)
- HTTP context: **`/hopper/`** — API base **`/hopper/api/`**
- Config file: `hopper-presentation.properties`

## Build

```bash
mvn clean install
```

Requires **Java 21** and Maven. Hop **2.18.1** is pulled from Maven Central / configured repos.

## Run (REST + editor)

Jetty is declared only on the **REST** module (the parent is a `pom` aggregator). Use one of:

```bash
# From the repository root: install modules, then run Jetty in the REST module
# (parent POM has no jetty plugin — bare "mvn jetty:run" at root fails with
# "No plugin found for prefix jetty")
mvn -pl hopper-presentation-rest -am install -DskipTests
cd hopper-presentation-rest && mvn jetty:run

# Or fully-qualified goal from the root:
# mvn -pl hopper-presentation-rest -am org.eclipse.jetty:jetty-maven-plugin:11.0.24:run
```

Point `metadata.path` at a folder with `presentation/`, `connector/`, `theme/`, `hopper-database-connection/` (see `hopper-presentation.properties`).

### Platform / Docker / SSO

Presentation joins the Data Hopper fleet (Keycloak realm `hopper`, role aliases, compose fragment) without Spring Boot. See **[docs/platform-sso-docker.md](docs/platform-sso-docker.md)**.

Then open:

`http://localhost:8080/hopper/api/render/main/`

Example property:

```properties
metadata.path=/data/hopper/metadata/
cors.allow.origin=true
```

## Architecture (short)

1. **Connectors** load/transform row data (SQL, sample, chain, sort, filter, REST, …).
2. **Components** (table, crosstab, charts, Gantt, label, text block, group, composite, …) layout and draw SVG.
3. **Presentations** compose pages, themes, interactions, and parameter mappings as Hop metadata JSON.
4. **REST** serves render pages, edit forms (annotation-driven), undo history, and the JS canvas editor.

## AI / automated authoring

Agents and tools should start from **[docs/ai/](docs/ai/README.md)**: wire-format notes, layout recipes, golden templates, JSON Schema export, validate/compile APIs (`/hopper/api/ai/*`), and a compact JSON DSL.

Core types: `HMetadataCodec`, `HMetadataValidator`, `HAuthoringDsl` in `hopper-presentation-core`.

## SVG components (artwork)

`HSvgComponent` loads SVG via Hop/`SvgCache` and **Apache Batik 1.19** (SVG 1.1). Artwork is embedded into the page SVG during server-side render.

**Authoring tips**

- Prefer SVG 1.1 primitives. Batik does **not** support SVG2-only filters such as `feDropShadow` (use `feGaussianBlur` + `feOffset` + `feFlood` + `feMerge` instead).
- Embedding composes with page/header/footer margin transforms so icons stay aligned with component bounds (including header logos).
- Scale types (`NONE`, `MIN`, `MAX`, `FILL`, …) control how natural SVG size maps into the layout box.

See [hopper-presentation-core/docs/components.md](hopper-presentation-core/docs/components.md).

## Editor and view sessions

The canvas editor and read-only viewer use in-memory **render IDs** (`POST /render/presentation` returns a UUID).

- **Edit → View (toolbar)** opens a new tab using the **current editor `renderId`**, so the edit session is not discarded.
- Avoid `reload: true` on that path: reload removes the cached rendering for the presentation name and would leave the Edit tab with a dead id (`Unable to find rendering with ID …`).
- Soft re-render after save/apply (`POST …/edit/presentation/{name}/render/`) replaces the editor render and updates the page’s `renderId` in place.

See [hopper-presentation-rest/README.md](hopper-presentation-rest/README.md).

## Icon

Project mark: `assets/icons/hopper-presentation.svg` — hopper funnel feeding a presentation canvas / chart. Packaged copies live under `hopper-presentation-rest` static resources (Batik-safe filters).

## Documentation

| Path | Content |
|------|---------|
| [hopper-presentation-core/README.md](hopper-presentation-core/README.md) | Core library overview |
| [hopper-presentation-core/docs/](hopper-presentation-core/docs/) | Architecture, API, components, connectors |
| [docs/security-and-audit.md](docs/security-and-audit.md) | Auth, action RBAC, usage audit plugins |
| [hopper-presentation-rest/README.md](hopper-presentation-rest/README.md) | REST API, run, smoke test |
| [hopper-presentation-rest/docs/smoke-test.md](hopper-presentation-rest/docs/smoke-test.md) | End-to-end render checklist |

## License

Apache License 2.0 — see [LICENSE](LICENSE).
