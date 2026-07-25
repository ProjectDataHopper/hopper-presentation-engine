# Authoring DSL (JSON v1)

Compact JSON compiled by `org.hopper.metadata.dsl.HAuthoringDsl` into full presentation/connector models (then Hop JSON).

**Not bidirectional** — do not expect full presentations to reverse into DSL.

## Presentation example

```json
{
  "kind": "presentation",
  "name": "ops-title-table",
  "description": "Title + table",
  "theme": "Default",
  "pages": [
    {
      "components": [
        {
          "name": "title",
          "type": "label",
          "text": "Operations",
          "place": { "recipe": "topLeft", "offset": [12, 12] }
        },
        {
          "name": "runs",
          "type": "table",
          "connector": "ops-runs-sample",
          "header": true,
          "columns": [
            { "column": "runId", "header": "Run ID", "width": 100 },
            { "column": "status", "header": "Status", "width": 90 }
          ],
          "place": { "recipe": "belowFill", "of": "title", "gap": 12, "bottomMargin": 20 }
        }
      ]
    }
  ]
}
```

## Connector example

```json
{
  "kind": "connector",
  "name": "ops-runs-sample",
  "type": "csv",
  "filename": "${HOPPER_METADATA_PATH}/sample/ops-runs.csv",
  "headerPresent": true,
  "separator": ",",
  "fields": [
    { "name": "runId", "type": "String" },
    { "name": "status", "type": "String" }
  ]
}
```

## Place recipes

| Recipe | Meaning |
|--------|---------|
| `topLeft` | page left+top + offset `[x,y]` |
| `fullPage` | all four page edges |
| `under` | top = peer BOTTOM; requires `of` |
| `rightOf` | left = peer RIGHT; requires `of` |
| `belowFill` | under + span width + optional bottomMargin |

Use `layoutRaw` for full attachment objects. Use `pluginExtra` for advanced plugin fields.

## Compile

```text
POST /hopper/api/ai/compile/presentation
{ "dsl": "<json string or object>" }
```
