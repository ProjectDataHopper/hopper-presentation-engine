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
| `HLabelComponent` | Label | Static or variable-substituted single-line text |
| `HTextBlockComponent` | Text block | Multi-line text with hard newlines, optional word wrap, dynamic height, optional line pagination |
| `HTableComponent` | Table | Tabular layout of connector rows; can paginate. Auto column width = max(header, body); `horizontalMargin` cell padding (default 1); empty selection = all connector columns |
| `HBarChartComponent` | Bar chart | Categories + values (+ optional series) |
| `HLineChartComponent` | Line chart | Categories + values (+ optional series) |
| `HPieChartComponent` | Pie chart | Categories + values (optional donut, legend, %) |
| `HGanttChartComponent` | Gantt chart | Horizontal task bars on a shared time axis |
| `HCrosstabComponent` | Crosstab | Horizontal/vertical dimensions + facts/aggregations |
| `HImageComponent` | Image | Raster image from path/URL (server-side load) |
| `HSvgComponent` | SVG | Embed/scale SVG artwork |
| `HPictorialChartComponent` | Pictorial chart | Bar-style: one step image (or clipped layers) per `categoryColumn` row; `valueColumn` maps to step %; labels above/below; fit-to-geometry. Assets: `${HOPPER_METADATA_PATH}/assets/…` (compact JPEG preferred) |
| `HCompositeComponent` | Composite | Nested child components |
| `HGroupComponent` | Group | Repeat a child layout per group key; nested connectors can be filtered by group keys (same-name match or explicit `keyMappings`) |

## Pictorial chart (`HPictorialChartComponent`)

Bar-style **image cells** instead of geometric bars — good for fill levels, gauges, and “real” icons (beer glasses, batteries, tanks).

### Data

| Field | Role |
|-------|------|
| `sourceConnectorName` | Row source |
| `categoryColumn` | One cell per row (like a bar category). **Empty** = single gauge from the first row |
| `valueColumn` | Metric mapped into the domain → percentage |
| `domainMin` / `domainMax` | Bounds for 0% / 100% (default `0` / `100`) |
| `seriesName` | Optional reusable **`pictorial-series`** metadata (preferred over inline maps) |

Values are **not clamped** to 0–100. Negative attainment can select a **broken** step (e.g. key `-100`); over 100% can select an **overflow** step (e.g. key `200`).

### Render modes

| Mode | Behaviour |
|------|-----------|
| `STEP_IMAGES` (default) | Pick the nearest step image from an integer-key map (`0`, `10`, …, `100`, plus optional extremes) |
| `CLIPPED_LAYERS` | Clip a full fill layer over an empty container (`backgroundImage` + `fillImage`, `clipDirection`) |

`stepQuantization`: `NEAREST` (default), `FLOOR`, or `CEIL`.

### Step resolution (extremes)

`HPictorialSeries.resolveStepPath` partitions keys:

- target **> 100** → only keys **> 100** when any exist (overflow glass), never a 100% step
- target **< 0** → only keys **< 0** when any exist
- otherwise → keys in **[0, 100]**

### Asset paths

Prefer portable paths:

```text
${HOPPER_METADATA_PATH}/assets/pictorial-series/{seriesName}/step_50.jpg
```

HTTP URLs and absolute paths work at render time but are harder to share. Compact **JPEG** is preferred for multi-step series.

### Labels / layout

- `showValueLabel` + `labelFormat` (e.g. `%.0f%%`) above each image
- `showCategoryLabel` below each image
- `itemGap` between multi-item cells; images **scale to fit** the component geometry (multi-item row)

### Series metadata (`pictorial-series`)

Hop metadata type **`pictorial-series`** (`HPictorialSeries`): reusable `imageMap`, prompts, `stepMin` / `stepMax` / `stepSize`, and clip layer paths. Managed in the admin **Pictorial Series** tab and referenced by components via `seriesName`.

### AI generation (design-time)

Admin can generate step ladders with providers **BUILTIN**, **XAI_GROK**, **OPENAI_DALLE**, **GOOGLE_IMAGEN**:

- Ladder **0–100** at `stepSize`, plus **one** image for `< 0` and **one** for `> 100` when `stepMin`/`stepMax` extend outside
- Three prompts: baseline (`{percentage}`), negative, overflow
- Output sizes from a provider-safe catalog (native aspect ratios; cover-crop when free sizes would letterbox)
- API keys: plain secret (Hop-obfuscated), `${ENV}`, or `#{gsm:secret-id:key}` — variables are **not** encrypted on save
- Settings file: `{metadata.path}/config/ai-pictorial-settings.json` (**do not commit secrets**)
- **Test Connection** performs a **live** credential probe (`GET /v1/models` for xAI/OpenAI; Google models list) — not a mere “key present” check

REST (admin): `GET/POST …/admin/pictorials/settings`, `POST …/test-connection`, `POST …/generate-series`, `POST …/generate-step`, `GET …/size-options`.

### Interactions

Whole-component only in v1 (no per-cell `DrawnItem` categories yet).

## Interaction locations (`getPossibleInteractionLocations`)

Authoring lists **whole component** first (host), then plugin options. Stored `itemCategory` must match `DrawnItem`s registered in `render()`.

| Component | Options (beyond whole-component) | DrawnItem categories |
|-----------|----------------------------------|----------------------|
| Label | Label text | `Label` |
| Text block | Text block content | `Text` |
| Table | Table cell, Table header | `Cell`, `Header` |
| Crosstab | Crosstab cell | `Cell` |
| Pie | Pie slice, Legend entry, Title | `ChartLabel`, `LegendEntry`, `Title` |
| Line | Series label, X/Y axis, Title | `ChartSeriesLabel`, `XAxisLabel`, `YAxisLabel`, `Title` |
| Bar | Bar/category, Category label, Y-axis, Legend, Title | `ChartLabel`, `XAxisLabel`, `YAxisLabel`, `LegendEntry`, `Title` |
| Gantt | Gantt bar, Title (if shown) | `GanttBar`, `Title` |
| Pictorial | *(none beyond whole-component)* | envelope only |
| Image / SVG | *(none)* | envelope only |
| Composite / Group | *(none)* | target **child** or synthetic instance names |

Click values for drill-down come from `DrawnContext.value` (slice label, cell text, task name, etc.).  
Parameter maps (`dimensionParameters`) read `DrawnContext.dimensionValues` (column → value). Tables, bar charts, and crosstabs populate this map at render time; a single-dimension hit also seeds it from `value`.

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
- **Interactions:** `DrawnItem`s for title, each slice (`ChartLabel`), and legend entries (`LegendEntry`). Authoring options from `getPossibleInteractionLocations()`: pie slice, legend entry, title (plus whole-component from the host). Slice/legend contexts include horizontal dimension columns for location matching.


## Text block (`HTextBlockComponent`)

Multi-line free text for notes, comments, and parameter-expanded content (`${PARAM}`).

- **Hard breaks:** `\n` / `\r\n` / `\r` always produce new lines (empty lines kept).
- **Soft wrap (default on):** word-wrap within the available width; oversized tokens break mid-word.
- **Wrap width:** final geometry width after left+right attachments, else optional `maxWidth`, else natural (hard breaks only).
- **Height:** grows with line count × font height × line spacing (+ margins), unless top+bottom fix height (then content is clipped).
- **Paginate:** when enabled and height is not fixed by attachments, whole lines continue on following render pages (same page-limit caps as tables).
- **Interactions:** whole-component plus “Text block content” (`DrawnItem.Category.Text`).
- **Editor:** text uses `HWidgetType.MULTI_LINE_TEXT` (textarea in generated forms).

Shared measurement lives in `HTextLayout` (FontMetrics greedy wrap) so table cell wrap can reuse it later.

## Gantt chart (`HGanttChartComponent`)

Horizontal task bars on a shared time axis (ops timelines, timings panels).

- **Data:** connector columns for task name, start/end (or duration), optional series/group; or embedded/inline tasks for tests and system panels.
- **Layout:** one row per task; bars scale to the min/max time span of the series.
- **Interactions:** Gantt bar (`GanttBar`) and optional title (`Title`); bar context carries task name/value for drill-down.

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
