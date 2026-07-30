# Interactions

An interaction = **method** (how) + **location** (where) + **actions** (what).

## Method

Wire value is an enum code string on the interaction:

| Code | Description |
|------|-------------|
| `SINGLE_CLICK` | Primary click (default) |
| `DOUBLE_CLICK` | Double-click |
| `MOUSE_HOVER` | Pointer hover (tooltips / popups) |

```json
"method": "SINGLE_CLICK"
```

The same drawn item may have **multiple** interactions (e.g. hover tooltip + click drill-down). Lookup returns all matches; the client filters by method.

### Viewer performance (region prefetch)

When a presentation **page opens** (or soft-reloads / page-switches), the view client loads:

```http
GET /hopper/api/render/info/interaction-regions/{renderId}/{pageNumber}
```

That response lists every interactive region on the page (hit/outline geometry, hit `DrawnContext`, and shared action definitions). Hover highlight and click resolution then run **locally** — no per-mousemove `lookupActions` round-trips. Authoring metadata is unchanged; this is a render-session optimization only.

## Location

```json
{
  "componentName": "sales-pie",
  "componentPluginId": "HPieChartComponent",
  "itemType": "ComponentItem",
  "itemCategory": "ChartLabel"
}
```

| Component | Typical `itemCategory` values |
|-----------|-------------------------------|
| Label | `Label` |
| Text block | `Text` |
| Table | `Cell`, `Header` |
| Crosstab | `Cell` |
| Pie | `ChartLabel`, `LegendEntry`, `Title` |
| Bar / Line | axis/series/title categories |
| Gantt | `GanttBar`, `Title` |
| Whole component | `ComponentArea` (always valid) |

Host always offers whole-component. Plugin options come from `getPossibleInteractionLocations()`.

## Actions

| `actionType` | Role |
|--------------|------|
| `OPEN_PRESENTATION` | Navigate to another presentation (`objectName` or cell value) |
| `OPEN_LINK_SAME_TAB` / `OPEN_LINK_NEW_TAB` | Open URL in `objectName` |
| `POPUP_CONTEXT_INFORMATION` | Hover tooltip: show hit `DrawnContext` value + dimension values; optional title in `objectName` |
| `POPUP_PRESENTATION` | Hover: soft-render target presentation (`objectName`) in a floating panel with param maps. Uses the **target presentation’s own layout mode and page size** (do not force continuous/viewport overrides — page-edge attachments must match the authored geometry, e.g. a 320×200 popup chart). |

Use `valueParameter` / `dimensionParameters` on open/popup-presentation actions to push hit values into presentation parameters. See core `api.md` parameters section.

### Parameter maps from hits (`DrawnContext`)

At render time, interactive regions store a `DrawnContext`:

| Field | Role |
|-------|------|
| `value` | Primary hit text (cell text, bar category label, slice label, …) |
| `dimensions` | Column metadata for the hit (table column, chart dim columns) |
| `dimensionValues` | **Map** `columnName → value` used by `dimensionParameters` |

**Required for drill-down filters:** `dimensionParameters` resolve from `dimensionValues`, not from `value` alone.

| Source | How `dimensionValues` is filled |
|--------|----------------------------------|
| **Table** cell/header | `{ columnName: cellText }` |
| **Bar chart** (category / bar / fact label) | Horizontal (and vertical series) combination values for that part |
| **Crosstab** | Intersection of horizontal + vertical combinations |
| **Single-dimension** hit | Constructor seeds `{ onlyDim: value }` when exactly one dimension column is present |

Action fields:

```json
{
  "actionType": "OPEN_PRESENTATION",
  "objectName": "Company Ships Detail",
  "dimensionParameters": [
    { "dimensionColumn": "company_name", "parameterName": "COMPANY_NAME" }
  ]
}
```

- `valueParameter` → sets one presentation parameter from `DrawnContext.value`
- `dimensionParameters[]` → each `{ dimensionColumn, parameterName }` reads `dimensionValues[dimensionColumn]`

Target presentations should declare the parameter (with optional default). Request/interaction values always win over defaults. Hierarchy: system variables → presentation defaults → mappings → **request/interaction**.

### Authoring in the editor

- **Presentation properties** — full interaction list for the presentation.
- **Component property form** — **Interactions** section lists only rules for that component; **+ Add** / Edit use the interaction builder and return to the form on save/cancel. Selection toolbar **Add interaction** does the same when the form is open.

### Example: hover popup + click open with dimension map

```json
"interactions": [
  {
    "method": "MOUSE_HOVER",
    "location": {
      "componentName": "companies-chart",
      "itemType": "ComponentItem",
      "itemCategory": "ChartLabel",
      "dimensionColumns": ["company_name"]
    },
    "actions": [
      {
        "actionType": "POPUP_PRESENTATION",
        "objectName": "Company Popup",
        "dimensionParameters": [
          { "dimensionColumn": "company_name", "parameterName": "COMPANY_NAME" }
        ]
      }
    ]
  },
  {
    "method": "SINGLE_CLICK",
    "location": {
      "componentName": "companies-table",
      "itemType": "ComponentItem",
      "itemCategory": "Cell",
      "dimensionColumns": ["company_name"]
    },
    "actions": [
      {
        "actionType": "OPEN_PRESENTATION",
        "objectName": "Company Ships Detail",
        "dimensionParameters": [
          { "dimensionColumn": "company_name", "parameterName": "COMPANY_NAME" }
        ]
      }
    ]
  }
]
```
