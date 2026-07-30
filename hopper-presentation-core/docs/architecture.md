# Hopper Presentation Engine Architecture

## Purpose

**Hopper Presentation Engine** renders presentations (reports and dashboards) **entirely on the server**:

1. **Data acquisition** runs server-side through pluggable **connectors**.
2. **Layout and drawing** run server-side into **SVG** (and optionally PDF).
3. Clients display the rendered result; they do not query databases or pull raw datasets into browser JavaScript.

This model suits enterprises that reject “data in the browser” architectures (JS calling web services or databases directly).

## High-level flow

```
HEnvironment.init()
        │
        ▼
HPresentation  (metadata: pages, components, connectors, themes, interactions)
        │
        ├─ doLayout(parentLog, renderContext, metadataProvider, parameters)
        │     for each page (topologically sorted components):
        │       processSourceData()  → connectors stream rows into component state
        │       doLayout()           → sizes, geometries, multi-page results
        │
        └─ render(layoutResults, metadataProvider)
              for each HRenderPage:
                draw background, header/footer, components onto SVGGraphics2D
                record DrawnItem hit regions for interactions
```

## Core concepts

| Concept | Type | Role |
|---------|------|------|
| **Presentation** | `HPresentation` | Top-level document (Hop `@HopMetadata`) |
| **Layout mode** | `HLayoutMode` | `paginated` (default) or `continuous` (browser scroll) |
| **Page** | `HPage` | Canvas with margins and components; fixed size when paginated; width/height resolved at layout when continuous |
| **Component** | `IHComponent` | Visual widget (table, chart, label, …) |
| **Connector** | `IHConnector` | Streaming data source or transform |
| **Theme** | `HTheme` | Colors and fonts |
| **Data context** | `IDataContext` | Variables + connector lookup + metadata provider |
| **Render context** | `IRenderContext` | Themes, stable colors, canvas size, optional continuous viewport |
| **Layout results** | `HLayoutResults` | Pages, geometries, SVG GCs, drawn items; continuous metrics when applicable |

## Plugin system

Data Hopper reuses Apache Hop’s plugin registry and **Jandex** annotation indexes:

- `@HComponentPlugin` + `HComponentPluginType`
- `@HConnectorPlugin` + `HConnectorPluginType`
- `@HAuditPlugin` + `HAuditPluginType` (usage / security audit sinks)
- Hop `@HopMetadata` types: presentation, connector, theme, hopper-database-connection

`HEnvironment.init()`:

1. Initializes `HopClientEnvironment` (value metas, databases, VFS, …).
2. Registers Data Hopper metadata/component/connector/audit plugin types.
3. Scans the classpath for annotated plugins.

Security and audit design: [security-and-audit.md](../../docs/security-and-audit.md).

Call `init()` once per JVM (thread-safe and idempotent).

## Layout pipeline (per component)

1. **`processSourceData`** — optionally run connectors and cache rows/aggregates.
2. **`getExpectedSize`** — fixed size from metadata or dynamic size from data.
3. **`getExpectedGeometry`** — resolve attachments (`HAttachment`) relative to page or other components.
4. **`doLayout`** — place results on one or more `HRenderPage`s (tables/crosstabs/text blocks may paginate).
5. **`render`** — paint using Batik `SVGGraphics2D` / Hop `HopSvgGraphics2D` with quality text hints (`HSvgRenderHints`: greyscale text anti-aliasing + fractional metrics). Soft-reload PNGs use the same hints in `HSvgToPng` so labels/tables stay readable on the HiDPI browser canvas.

Components on a page are ordered via a **topological sort** of attachment dependencies.

Some components **re-measure in `doLayout` after geometry is known**. For example, `HTextBlockComponent` word-wraps to the final width from left+right attachments (or `maxWidth`), then sets height from the resulting line count. Tables and text blocks that paginate honor **`server.layout.max-render-pages`** (`HLayoutPageLimitSettings`) so runaway content cannot create unbounded render pages.

### Continuous (web) layout

When `HPresentation.layoutMode` is `continuous` (or the render context sets continuous scroll):

1. Effective **page width** = client `viewportWidth` (clamped 320–2400), else `designWidth`, else 1200.
2. A **provisional tall page** is used so tables/crosstabs/text pack into a single part (no multi-page splits).
3. After component layout, **page height** is set from content extent, capped at **5000 CSS-px** usable height (`Constants.DEFAULT_MAX_CONTINUOUS_CONTENT_HEIGHT`). Overflow sets `contentTruncated`.
4. The REST **view** shell shows one surface with a **native vertical scrollbar**; page arrows are hidden. The **icon toolbar is sticky** at the top of the scroll shell while content scrolls beneath.
5. The **editor** for continuous presentations uses the same continuous layout (design width fallback, content height growth) with a scrollable main column and sticky toolbar. View mode additionally re-lays out to the live browser width.

**PDF export:** `POST /hopper/api/render/export/pdf` builds a multi-page PDF (SVG → FOP/Batik → PDFBox merge). Paginated sessions can export the current layout; continuous presentations **re-layout as paginated** for a chosen paper size (dialog in the UI). See `HSvgPdfExporter` / `HPdfPaper`.

Paginated mode remains the default for print-oriented documents.

## Data streaming contract

Connectors implement `IHDataStreaming`:

- `describeOutput(IDataContext)` → `IRowMeta`
- `startStreaming(IDataContext)` → push rows to `IHRowListener`s
- end-of-stream signal: `rowReceived(null, null)` via `outputDone()`
- `waitUntilFinished()` for async sources

Transforms (sort, filter, distinct, selection, chain, passthrough) attach listeners to a **source connector name**, then start the source.

`HConnector.retrieveRows()` is a convenience that collects all rows for parameter mapping and tests.

## Interactions

After render, geometry of interactive regions is stored as `DrawnItem`s. A host application (e.g. hopper-presentation-rest) maps pointer coordinates to items and evaluates `HInteraction` rules (parameters, navigation).

### Authoring

Each component plugin can declare hit targets via `IHComponent.getPossibleInteractionLocations()` (returns `HInteractionLocationOption`s). The host always prepends a **whole-component** option. Stored interactions use `HInteraction` → method (single/double click) + `HInteractionLocation` (`itemType` / `itemCategory` / `dimensionColumns` aligned with `DrawnItem`s) + `List<HInteractionAction>`.

The presentation editor opens a component-scoped interaction builder from the selection toolbar (“Add interaction”).

## Rendering outputs

| Output | Implementation |
|--------|----------------|
| SVG | Primary path; one document per render page |
| PDF | FOP / PDFBox helpers under `org.hopper.render.pdf` |
| HTML | Host concern (hopper-presentation-rest wraps SVG in pages) |

## Threading notes

- `HEnvironment` / Hop `PluginRegistry` initialization must be sequential.
- A connector instance should not be `startStreaming`’d twice without finishing (internal queues).
- `PresentationDataContext.getConnector()` returns a **copy** so concurrent queries stay isolated.

## Related modules

- **hopper-presentation-rest** — HTTP metadata + render API
- **hopper-hop-plugins** — pipeline connector and pipeline/workflow diagram components
- **hop-hopper-plugins** — Hop GUI AutoDoc
