# Components (agent cheat sheet)

Full detail: [hopper-presentation-core/docs/components.md](../hopper-presentation-core/docs/components.md).

| Alias | Plugin id | Key fields |
|-------|-----------|------------|
| `label` | `HLabelComponent` | `label` (variables OK) |
| `text` | `HTextBlockComponent` | `text`, `wrap`, `maxWidth`, `paginate` |
| `table` | `HTableComponent` | `sourceConnectorName`, `columnSelection[]`, `header` |
| `bar` / `line` / `pie` | chart plugins | dimensions + facts, `sourceConnectorName` |
| `gantt` | `HGanttChartComponent` | task/start/end columns or embedded tasks |
| `crosstab` | `HCrosstabComponent` | horizontal/vertical dims + facts |
| `image` / `svg` | media | `filename` |
| `group` / `composite` | nesting | child components |

## Table columns

```json
"columnSelection": [
  { "columnName": "runId", "headerValue": "Run ID", "width": 100 }
]
```

## Text block

Multi-line free text; soft wrap uses geometry width or `maxWidth`. Prefer for notes / `${PARAM}` paragraphs.

## Pitfalls

- Labels are **single-line**; use text block for newlines.
- Charts need non-empty dimension/fact config for meaningful output.
- Interaction categories must match plugin `DrawnItem`s (see [interactions.md](interactions.md)).
