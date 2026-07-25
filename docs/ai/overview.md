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
- **Layout mode** (`layoutMode`):
  - `paginated` (default) — fixed page size (e.g. A4); long tables/text split across render pages.
  - `continuous` — browser-oriented: width from client viewport (or `designWidth` fallback, default 1200 CSS-px); height grows with content up to a server cap (~5000 usable CSS-px); view uses a native vertical scrollbar instead of page arrows.
- **PDF export** — always multi-page fixed sheets (`POST /hopper/api/render/export/pdf`). Continuous documents are re-laid out as paginated for a chosen paper size and light/dark mode (light recommended for print). Do not invent client-side PDF generation.

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
