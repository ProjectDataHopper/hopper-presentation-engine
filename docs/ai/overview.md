# Overview

Hopper presentations are **server-side SVG** documents: connectors stream rows, components layout and draw, the browser displays rendered pages.

## Mental model

```text
connector (data) ──► component (visual) ──► page ──► presentation
         ▲                    │
         └── sourceConnectorName / attachments
```

- **Connectors** live in the shared `connector/` catalog (not embedded in presentation JSON).
- **Components** reference connectors by **name**.
- **Layout** is relative via attachments (page or other component).
- **Themes** are catalog names (`defaultThemeName`).

## Authoring principles for agents

1. Prefer **templates** and **place recipes** over inventing attachment math.
2. Use **stable unique component names** (`title`, `runs-table`); attachments reference those names.
3. Keep JSON **minimal** (omit nulls / default noise).
4. After drafting, **validate** (`docs/ai/README.md` closed loop).
5. Do not invent plugin ids — use `GET /hopper/api/ai/context` or plugin lists.

## Related engine docs

- [architecture.md](../hopper-presentation-core/docs/architecture.md)
- [components.md](../hopper-presentation-core/docs/components.md)
- [connectors.md](../hopper-presentation-core/docs/connectors.md)
- [api.md](../hopper-presentation-core/docs/api.md)
