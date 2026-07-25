# Recipe: SQL → bar chart

1. Ensure a `hopper-database-connection` exists.
2. Connector: `SqlConnector` with `databaseConnectionName` + `sql` returning category + value columns.
3. Presentation: `HBarChartComponent` with horizontal dimensions (category) and one fact (value).
4. Layout: title label + chart `belowFill`.
5. Validate; smoke layout needs the DB reachable for full data (structure validates without).
