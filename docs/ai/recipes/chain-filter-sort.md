# Recipe: chain / filter / sort

1. Source connector (CSV/SQL/sample) with a clear name.
2. `SimpleFilterConnector` / `SortConnector` with `sourceConnectorName` pointing at the source.
3. Or encapsulate steps in `ChainConnector` (see core connectors doc for external-source pattern).
4. Components bind to the **last** transform name, not the raw source (unless passthrough).
