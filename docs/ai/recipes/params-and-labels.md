# Recipe: parameters + labels

1. Label text: `"Region: ${REGION}"`.
2. `parameterMappings` optional for defaults / connector-driven params (see core api.md).
3. Interactions can set parameters from clicked chart/table values (`valueParameter`).
4. Re-render with request parameters applied last (always win).
