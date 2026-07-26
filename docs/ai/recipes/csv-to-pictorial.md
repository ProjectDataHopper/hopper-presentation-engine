# Recipe: CSV → pictorial chart

Bar-style **image cells** (step sequence or clipped layers) driven by connector rows.

## 1. Series assets

Create a reusable **`pictorial-series`** in Admin → **Pictorial Series**, or drop images under:

```text
${HOPPER_METADATA_PATH}/assets/pictorial-series/{seriesName}/
  step_0.jpg … step_100.jpg
  step_-100.jpg   # optional negative extreme
  step_200.jpg    # optional overflow extreme
```

Admin **Generate series** can build the ladder with AI (xAI / OpenAI / Google / built-in). Configure the API key as `${ENV}` or `#{gsm:…}` and use **Test Connection** for a live credential probe.

## 2. Connector

CSV (or SQL) with at least:

| Column | Role |
|--------|------|
| category | Label under each cell (optional for single gauge) |
| value | Metric mapped with `domainMin` / `domainMax` → % |

Example connector body (canonical Hop shape):

```json
{
  "name": "beer-sales-2025",
  "shared": true,
  "connector": {
    "CsvConnector": {
      "pluginId": "CsvConnector",
      "filename": "${HOPPER_METADATA_PATH}/sample/beer-sales-2025.csv",
      "headerPresent": true,
      "separator": ",",
      "fields": [
        { "name": "region", "type": "String" },
        { "name": "pct_of_target", "type": "Number" }
      ]
    }
  }
}
```

## 3. Presentation component

```json
{
  "name": "pictorial-gauge",
  "layout": {
    "left": { "offset": 12, "percentage": 0, "alignment": "LEFT" },
    "top": { "offset": 48, "percentage": 0, "alignment": "TOP" },
    "right": { "offset": 12, "percentage": 0, "alignment": "RIGHT" },
    "bottom": { "offset": 12, "percentage": 0, "alignment": "BOTTOM" }
  },
  "component": {
    "HPictorialChartComponent": {
      "pluginId": "HPictorialChartComponent",
      "sourceConnectorName": "beer-sales-2025",
      "seriesName": "beers",
      "categoryColumn": "region",
      "valueColumn": "pct_of_target",
      "domainMin": "0",
      "domainMax": "100",
      "renderMode": "STEP_IMAGES",
      "stepQuantization": "NEAREST",
      "showValueLabel": true,
      "showCategoryLabel": true,
      "labelFormat": "%.0f%%",
      "itemGap": "12"
    }
  }
}
```

Golden template: [../templates/presentation-pictorial-chart.json](../templates/presentation-pictorial-chart.json).

## 4. Layout tips

- Prefer **`layoutMode": "continuous"`** for dashboards with a single tall/wide pictorial strip.
- Multi-item rows need enough width; the component scales images to fit and spaces them with `itemGap`.
- Single gauge: omit `categoryColumn` (first connector row only).

## 5. Extremes

Values **outside** 0–100% are intentional:

| Value | Expected step partition |
|-------|-------------------------|
| `< 0` | Negative keys only (e.g. broken glass at `-100`) |
| `0…100` | In-range ladder |
| `> 100` | Overflow keys only (e.g. overflowing glass at `200`) |

Without extreme keys, lookup falls back to the nearest overall step.

## 6. Validate

```text
POST /hopper/api/ai/validate/presentation  { "json": "…", "smoke": true }
```

Checklist: connector name matches, series exists (or inline `imageMap` paths resolve), asset paths use `${HOPPER_METADATA_PATH}`, no invented plugin ids.
