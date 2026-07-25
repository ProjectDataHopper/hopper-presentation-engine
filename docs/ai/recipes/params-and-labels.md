# Recipe: parameters + labels

1. Declare parameters on the presentation (`parameters`: name, description, defaultValue).
2. Label text: `"Region: ${REGION}"` (uses default or request value).
3. Optional `parameterMappings` for connector-driven params (see core api.md).
4. Interactions set parameters from clicked chart/table values (`valueParameter` — pickers list declared names).
5. Hierarchy: system variables → presentation defaults → mappings → request/interaction (always win).
