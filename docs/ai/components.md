# Components (agent cheat sheet)

Full detail: [hopper-presentation-core/docs/components.md](../hopper-presentation-core/docs/components.md).

| Alias | Plugin id | Key fields |
|-------|-----------|------------|
| `label` | `HLabelComponent` | `label` (variables OK) |
| `text` | `HTextBlockComponent` | `text`, `wrap`, `maxWidth`, `paginate` |
| `table` | `HTableComponent` | `sourceConnectorName`, `columnSelection[]`, `header`, `horizontalMargin` (cell padding vs grid; default 1) |
| `bar` / `line` / `pie` | chart plugins | dimensions + facts, `sourceConnectorName` |
| `gantt` | `HGanttChartComponent` | task/start/end columns or embedded tasks |
| `crosstab` | `HCrosstabComponent` | horizontal/vertical dims + facts |
| `image` / `svg` | media | `filename` |
| `pictorial` | `HPictorialChartComponent` | Bar-style cells: `categoryColumn` + `valueColumn` + `seriesName`; `domainMin`/`domainMax`; `renderMode` `STEP_IMAGES` \| `CLIPPED_LAYERS`; % **not** clamped (negative → broken step, &gt;100 → overflow); labels above/below; `itemGap` |
| `group` / `composite` | nesting | child components |

## Pictorial chart

Copy [templates/presentation-pictorial-chart.json](templates/presentation-pictorial-chart.json). Recipe: [recipes/csv-to-pictorial.md](recipes/csv-to-pictorial.md).

| Concern | Rule |
|---------|------|
| Series | Prefer catalog `pictorial-series` via `seriesName` (admin tab) over inline `imageMap` |
| Paths | `${HOPPER_METADATA_PATH}/assets/pictorial-series/{name}/…` |
| Extremes | Step keys may be negative or &gt;100; resolution **partitions** extremes so 150% does not pick the 100% image when an overflow key exists |
| Multi-item | Set `categoryColumn`; one image cell per connector row |
| Single gauge | Leave `categoryColumn` empty (first row only) |
| AI assets | Design-time only (admin generate-series / generate-step); never put API keys in presentation JSON |

Schema: [schemas/plugins/components/HPictorialChartComponent.schema.json](schemas/plugins/components/HPictorialChartComponent.schema.json).

## Table columns

```json
"columnSelection": [
  { "columnName": "runId", "headerValue": "Run ID", "width": 100 },
  { "columnName": "status", "headerValue": "Status", "width": 0 }
]
```

| Field | Notes |
|-------|--------|
| `width` | `0` / omitted = **auto** (max of header text + body text + cell margins). Positive = fixed content width in CSS-px. |
| `horizontalMargin` | Horizontal **cell** padding vs vertical grid lines (default **1**). |
| `verticalMargin` | Vertical cell padding vs horizontal grid lines. |

Empty `columnSelection` with a source connector means **all connector columns** (auto-filled before measure). In the editor, column lists have **All columns** to append missing connector fields.

Property **preview** lays out at **natural content size** so auto column widths (including headers) are not force-shrunk into a fixed 320×200 box.

## Text block

Multi-line free text; soft wrap uses geometry width or `maxWidth`. Prefer for notes / `${PARAM}` paragraphs.

## Pitfalls

- Labels are **single-line**; use text block for newlines.
- Charts need non-empty dimension/fact config for meaningful output.
- Interaction categories must match plugin `DrawnItem`s (see [interactions.md](interactions.md)).
- Drill-down filters need `dimensionParameters` + hit `dimensionValues` (see [interactions.md](interactions.md)); table cells and bar categories populate these maps at render time.
