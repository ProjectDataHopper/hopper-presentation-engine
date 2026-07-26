# AI / automated authoring

This folder is the **entry point for agents and tools** that generate Hopper presentations and connectors.

## Read in this order

1. [wire-format.md](wire-format.md) — JSON shapes (Hop polymorphic vs flat)
2. [overview.md](overview.md) — catalog layout, themes, variables
3. [layout.md](layout.md) — attachments and place recipes
4. [connectors.md](connectors.md) / [components.md](components.md) — plugin cheat sheets
5. [interactions.md](interactions.md) — click targets
6. [recipes/](recipes/) — end-to-end patterns (including [csv-to-pictorial.md](recipes/csv-to-pictorial.md))
7. [templates/](templates/) — golden JSON (copy and edit; includes pictorial chart)
8. [dsl/](dsl/) — compact JSON authoring language (`pictorial` / `pictorialchart` aliases)

Deep human docs remain under [hopper-presentation-core/docs/](../hopper-presentation-core/docs/).

## Closed loop (recommended)

```text
draft JSON or DSL
    → POST /hopper/api/ai/validate/presentation  { "json": "..." }
    → fix errors by code (ATTACHMENT_MISSING, UNKNOWN_PLUGIN, …)
    → optional smoke: { "smoke": true }
    → save via existing metadata APIs (human confirm)
```

DSL path:

```text
POST /hopper/api/ai/compile/presentation  { "dsl": "{ ... }" }
  → returns Hop JSON + validation report
```

CLI (core classpath):

```bash
java ... org.hopper.metadata.validate.HMetadataValidateMain \
  --type presentation --file my.json [--catalog metadata_dir] [--smoke]
```

## Bootstrap API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/hopper/api/ai/context` | Plugin list + doc pointers |
| GET | `/hopper/api/ai/schemas/presentation` | JSON Schema (file shape) |
| GET | `/hopper/api/ai/schemas/connector` | JSON Schema |
| POST | `/hopper/api/ai/validate/presentation` | Structural (+ optional smoke) |
| POST | `/hopper/api/ai/validate/connector` | Structural |
| POST | `/hopper/api/ai/compile/presentation` | DSL → Hop JSON |
| POST | `/hopper/api/ai/compile/connector` | DSL → Hop JSON |

**Never auto-save** AI output into production metadata without review.
