# Wire format

## Metadata folders

Under `metadata.path` (see `hopper-presentation.properties`):

| Folder | Type key | Contents |
|--------|----------|----------|
| `presentation/` | `presentation` | One JSON file per presentation |
| `connector/` | `connector` | One JSON file per connector |
| `theme/` | `theme` | Themes |
| `hopper-database-connection/` | `hopper-database-connection` | DB connections |

## Two plugin JSON shapes

### Canonical file shape (Hop `JsonMetadataParser`)

Used by REST metadata load/save and files on disk:

```json
{
  "name": "my-table",
  "layout": { "left": { "offset": 12, "alignment": "LEFT" }, "top": { "offset": 48, "alignment": "TOP" } },
  "component": {
    "HTableComponent": {
      "pluginId": "HTableComponent",
      "sourceConnectorName": "ops-runs-sample",
      "header": true
    }
  }
}
```

Connector:

```json
{
  "name": "ops-runs-sample",
  "shared": true,
  "connector": {
    "CsvConnector": {
      "pluginId": "CsvConnector",
      "filename": "${HOPPER_METADATA_PATH}/sample/ops-runs.csv",
      "headerPresent": true,
      "separator": ",",
      "fields": [ { "name": "runId", "type": "String" } ]
    }
  }
}
```

### Flat Jackson shape (also accepted)

Used by some tests and `HPresentation.toJsonString()`:

```json
{
  "name": "my-table",
  "component": {
    "pluginId": "HTableComponent",
    "sourceConnectorName": "ops-runs-sample"
  }
}
```

**Parser:** `org.hopper.metadata.codec.HMetadataCodec` accepts both.

## Variables

| Variable | Meaning |
|----------|---------|
| `${PARAM}` | Presentation / request parameter |
| `${HOPPER_METADATA_PATH}` | Metadata root folder |
| `${HOPPER_DATA_PATH}` | Runtime data (cache, timings) |

## Minimal presentation skeleton

```json
{
  "name": "example",
  "description": "Minimal",
  "defaultThemeName": "Default",
  "pages": [
    {
      "components": [
        {
          "name": "title",
          "layout": {
            "left": { "offset": 12, "percentage": 0, "alignment": "LEFT" },
            "top": { "offset": 12, "percentage": 0, "alignment": "TOP" }
          },
          "component": {
            "HLabelComponent": {
              "pluginId": "HLabelComponent",
              "label": "Hello"
            }
          }
        }
      ]
    }
  ],
  "interactions": [],
  "parameterMappings": []
}
```
