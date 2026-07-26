# Recipe: parameters + labels

1. Declare parameters on the presentation (`parameters`: name, description, defaultValue).
2. Label text: `"Region: ${REGION}"` (uses default or request value).
3. Optional `parameterMappings` for connector-driven params (see core api.md).
4. Interactions set parameters from clicked chart/table values:
   - `valueParameter` — one param from the hit’s primary `DrawnContext.value`
   - `dimensionParameters` — map hit `dimensionValues[column]` → presentation param (prefer this for filters such as `company_name` → `COMPANY_NAME`)
5. Hierarchy: system variables → presentation defaults → mappings → request/interaction (always win).

## Drill-down filter (table or bar chart)

```json
{
  "actionType": "OPEN_PRESENTATION",
  "objectName": "Company Ships Detail",
  "dimensionParameters": [
    { "dimensionColumn": "company_name", "parameterName": "COMPANY_NAME" }
  ]
}
```

Target presentation declares `COMPANY_NAME` (optional default). Connector filter uses `${COMPANY_NAME}`.  
Hover popup: same map with `POPUP_PRESENTATION` — soft-render uses the target’s own page size and the mapped parameters (not continuous viewport overrides).
