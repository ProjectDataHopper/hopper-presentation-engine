# Recipe: CSV → table

1. Create connector from [templates/connector-csv.json](../templates/connector-csv.json); set `filename` and `fields`.
2. Create presentation from [templates/presentation-title-table.json](../templates/presentation-title-table.json).
3. Set table `sourceConnectorName` to the connector name.
4. Align `columnSelection` with CSV field names.
5. Validate presentation JSON.

DSL alternative: [dsl/README.md](../dsl/README.md) connector + table components.
