# Interactions

An interaction = **method** (click) + **location** (component / item category) + **actions** (set parameters, navigate).

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

Use `valueParameter` / `dimensionParameters` on actions to push clicked values into presentation parameters. See core `api.md` parameters section.
