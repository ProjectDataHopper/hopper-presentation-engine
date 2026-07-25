# Layout attachments

Each component has `layout` with optional `left`, `right`, `top`, `bottom` attachments.

```text
attachment = {
  componentName: null | "OtherComponent",  // null = page edge
  percentage: 0,                           // % of reference size
  offset: 12,                              // pixels
  alignment: LEFT|RIGHT|TOP|BOTTOM|CENTER|DEFAULT
}
```

## Rules of thumb

- **Left** attachments use horizontal alignments (LEFT/RIGHT/CENTER).
- **Top** attachments use vertical alignments (TOP/BOTTOM/CENTER).
- Peer names must match another component **`name`** on the same page.
- Cycles (A depends on B depends on A) are invalid.
- Order of components in JSON does not need to match dependency order (engine topologically sorts).

## Recipes (copy-paste)

### Top-left of page

```json
"layout": {
  "left": { "offset": 12, "percentage": 0, "alignment": "LEFT" },
  "top":  { "offset": 12, "percentage": 0, "alignment": "TOP" }
}
```

### Full page (stretch)

```json
"layout": {
  "left":   { "offset": 0, "percentage": 0, "alignment": "LEFT" },
  "top":    { "offset": 0, "percentage": 0, "alignment": "TOP" },
  "right":  { "offset": 0, "percentage": 0, "alignment": "RIGHT" },
  "bottom": { "offset": 0, "percentage": 0, "alignment": "BOTTOM" }
}
```

### Under another component

```json
"layout": {
  "left": { "componentName": "title", "offset": 0, "percentage": 0, "alignment": "LEFT" },
  "top":  { "componentName": "title", "offset": 12, "percentage": 0, "alignment": "BOTTOM" },
  "right": { "offset": 12, "percentage": 0, "alignment": "RIGHT" }
}
```

### Right of another component

```json
"layout": {
  "left": { "componentName": "sidebar", "offset": 12, "percentage": 0, "alignment": "RIGHT" },
  "top":  { "componentName": "sidebar", "offset": 0, "percentage": 0, "alignment": "TOP" }
}
```

### Fixed box from top-left (width/height via right/bottom offsets)

Editor often stores right/bottom as offsets from page TOP/LEFT (not always page RIGHT). When unsure, use **DSL place recipes** (`topLeft`, `under`, `belowFill`) and compile.

## DSL place recipes

See [dsl/README.md](dsl/README.md): `topLeft`, `fullPage`, `under`, `rightOf`, `belowFill`.
