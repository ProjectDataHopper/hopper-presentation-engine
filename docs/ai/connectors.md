# Connectors (agent cheat sheet)

Full detail: [hopper-presentation-core/docs/connectors.md](../hopper-presentation-core/docs/connectors.md).

| Alias (DSL) | Plugin id | Role | Key fields |
|-------------|-----------|------|------------|
| `csv` | `CsvConnector` | File rows | `filename`, `headerPresent`, `separator`, `fields[]` |
| `sql` | `SqlConnector` | JDBC | `databaseConnectionName`, `sql` |
| `sample` | `SampleDataConnector` | Dev rows | `rowCount` |
| `rest` | `HRestConnector` | HTTP JSON | `url`, `body`, field tags |
| `binary` | `BinaryRowsConnector` | `.hoprows` file | `filename` |
| `sort` | `SortConnector` | Transform | `sourceConnectorName`, sort columns |
| `filter` | `SimpleFilterConnector` | Transform | `sourceConnectorName`, filters |
| `chain` | `ChainConnector` | Pipeline | nested steps |

## CSV template

See [templates/connector-csv.json](templates/connector-csv.json).

## Pitfalls

- Connector **names** are catalog keys; components must use exact `sourceConnectorName`.
- SQL needs a `hopper-database-connection` entry by name.
- Prefer variables for paths: `${HOPPER_METADATA_PATH}/sample/file.csv`.
- Chain steps currently expect an external source (see core connectors doc).
