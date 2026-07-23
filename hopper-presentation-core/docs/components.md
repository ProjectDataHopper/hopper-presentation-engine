# Data Hopper Components

Components are visual building blocks on a `HPage`. Each implements `IHComponent` and is registered with `@HComponentPlugin`.

## Layout properties (common)

Inherited via `HBaseComponent` / `HComponent` wrapper:

- **Name** — unique on the page (attachment target)
- **Size** — optional fixed width/height; otherwise dynamic
- **Layout / attachments** — `HLayout` with left/right/top/bottom `HAttachment`s (percentage, offset, relative component)
- **Theme** — optional override of presentation default theme
- **Source connector** — name of a presentation connector for data-driven components
- **Background / border / fonts / colors**

## Built-in components

| Plugin ID | Class | Description |
|-----------|-------|-------------|
| `HLabelComponent` | Label | Static or variable-substituted text |
| `HTableComponent` | Table | Tabular layout of connector rows; can paginate |
| `HBarChartComponent` | Bar chart | Categories + values (+ optional series) |
| `HLineChartComponent` | Line chart | Categories + values (+ optional series) |
| `HPieChartComponent` | Pie chart | Categories + values (optional donut, legend, %) |
| `HCrosstabComponent` | Crosstab | Horizontal/vertical dimensions + facts/aggregations |
| `HImageComponent` | Image | Raster image from path/URL (server-side load) |
| `HSvgComponent` | SVG | Embed/scale SVG artwork |
| `HCompositeComponent` | Composite | Nested child components |
| `HGroupComponent` | Group | Repeat a child layout per group key; nested connectors can be filtered by group keys (same-name match or explicit `keyMappings`) |

## Plugin icons (`@HComponentPlugin.image`)

Component type icons ship **with the plugin JAR** (hopper-presentation-core for built-ins).

```java
@HComponentPlugin(
    id = "HLabelComponent",
    name = "Label",
    description = "...",
    image = "ui/images/components/label.svg")
```

| Piece | Detail |
|--------|--------|
| Resources | `ui/images/components/*.svg` under hopper-presentation-core |
| Registration | `HComponentPluginType.extractImageFile` → `IPlugin.getImageFile()` |
| List API | `GET plugins/components` includes `"image"` |
| Image API | `GET plugins/components/{pluginId}/image` |
| Browser | Palette + page component list use the image API with name/description tooltips |

## Pie chart (`HPieChartComponent`)

- **Data:** one or more **horizontal dimensions** (slice categories; multi-dim labels join with `-`) and **exactly one fact** with aggregation (SUM / COUNT / AVERAGE as supported by the pivot). Vertical dimensions are ignored in v1.
- **Options:** title, margins, legend (`RIGHT` / `BOTTOM`), on-slice labels, percentages, fact values, **inner radius %** (0 = pie, e.g. 50 = donut), start angle (degrees, default −90 = top), clockwise.
- **Values:** null/missing → 0; **negative values are skipped**; total 0 draws an empty outline only.
- **Colors:** theme stable colors keyed by category label (`getStableColor`).
- **Interactions:** `DrawnItem`s for title, each slice (`ChartLabel`), and legend entries (`LegendEntry`).

## Data-driven components

Typical pattern:

1. Resolve connector from `IDataContext`.
2. In `processSourceData`, stream or retrieve rows and compute aggregates/geometry hints.
3. In `doLayout`, allocate one or more `HComponentLayoutResult` entries (possibly multi-page).
4. In `render`, draw using the page’s `SVGGraphics2D` and register `DrawnItem`s if interactive.

## Attachment model

Attachments anchor a side of a component to:

- the **page** (null component name), or
- another **component** by name,

with optional **percentage** of the reference size and **pixel offset**.

Circular attachment graphs are invalid; the page sorts components topologically before layout.

## Browser configuration forms (`@HWidgetElement`)

Plugin classes declare editor widgets with Data Hopper’s `@HWidgetElement` next to `@HopMetadataProperty`.
Example: `HLabelComponent`. Annotations are scanned into `HGuiRegistry` during
`HEnvironment.init()`; `GuiFormSchemaBuilder` exports a UI-agnostic schema.

```java
@HWidgetElement(
    order = "10000-label",
    parentId = HGuiFormConstants.PARENT_PLUGIN,
    type = HWidgetType.TEXT,
    label = "Label text",
    tabName = "",           // optional: group fields onto separate editor tabs
    tabTooltip = "")
@HopMetadataProperty
private String label;
```

Shared parent ids live in `org.hopper.core.gui.form.HGuiFormConstants`:

| Parent id | Section |
|-----------|---------|
| `HComponent-Plugin` | Plugin-specific fields |
| `HComponent-Base` | Fields on `HBaseComponent` |
| `HComponent-Wrapper` / `HComponent-Layout` | Shared chrome (generated) |

Optional `tabName` / `tabTooltip` on the annotation (and on `GuiFormField`) let clients group
widgets onto separate tabs within a section.

### Dynamic combo sources (`HComboSource`)

| Source | Options filled from |
|--------|---------------------|
| `CONNECTORS` | Presentation-local connectors + shared `connector` metadata |
| `THEMES` | `theme` metadata names |
| `COMPONENTS` | Component names on the current render page (layout attachments) |
| `CONNECTOR_COLUMNS` | Output fields of the connector named by `dependsOn` (default `sourceConnectorName`) |
| `METADATA` | Element names for `metadataKey` (e.g. `hopper-database-connection`) |

hopper-presentation-rest binds these via `bindSelectSource()` and refreshes column options when the source
connector combo changes.

`GuiFormHtmlRenderer` emits hopper-presentation-rest side-panel HTML. See hopper-presentation-rest `EditPluginResource`
(`edit/component/{id}/`, `edit/schema/component/{id}/`).

Static per-plugin HTML under hopper-presentation-rest has been removed; forms are **always** generated.

After `mvn test -Dtest=FormEndToEndTest`, open `target/form-review/README.md` for a local
gallery of generated schemas and form HTML previews.

## Nested component editors

`HGroupComponent.groupComponent` (`COMPONENT`) and `HCompositeComponent.children`
(`LIST` + `itemKind=component`) use a **component catalog** embedded in the form schema
(`GuiFormSchema.componentCatalog`). The hopper-presentation-rest side panel builds nested name/type/layout/plugin
fields recursively via `setNestedComponent` / `setNestedComponentList` in `hopper-presentation.js`.

## SVG component (`HSvgComponent`)

Embeds an external SVG file (classpath or VFS path after variable resolution) into the presentation page.

| Topic | Detail |
|--------|--------|
| Load path | Hop `SvgCache` / Batik DOM (SVG **1.1**) |
| Scale | `ScaleType`: `NONE`, `MIN`, `MAX`, `FILL`, `FILL_HORIZONTAL`, `FILL_VERTICAL` |
| Embed | Children of the source root are copied into a `<g>`; position/scale compose with the current `Graphics2D` transform (page margins, header/footer offsets) |
| Icons | Keep filters SVG 1.1-compatible — **no** `feDropShadow` (unsupported by Batik 1.19). Soft shadows: `feGaussianBlur` → `feOffset` → `feFlood` → `feComposite` → `feMerge` |

Regression: header logo + page margins must keep icon and border bounds aligned (`InventoryPitHeaderSvgTest`).

## Known limitations (backlog)

- Crosstab: subtotals, multi-level sort, configurable “Total” label
- SVG component: centered bounds with magnification when both left and right (or top and bottom) attachments stretch the box
- DrawnItem rotation
- `List<HSortMethod>` and arbitrary bean lists (column/fact/string/component kinds today)
- Connector edit UI in hopper-presentation-rest shell (schema API exists; page chrome is presentation-focused)
- Nested layout “relative to” dropdown only lists top-level page component names
