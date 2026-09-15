# hopper-presentation-swt

SWT / Hop GUI viewer for Hopper presentations. Renders **server-side SVG** via
`hopper-presentation-core` (`HPresentationSession`) — **no web server**.

## Chrome

| Mode | Toolbar | Use |
|------|---------|-----|
| `FULL` | home, zoom in/out/100%, fit page/width/height, paging, refresh | Explorer tab / dashboard |
| `MINIMAL` | refresh + paging | Dialogs / cards |
| `NONE` | none | Small chart in a pane |

The viewer **fits the page to the canvas** on open and on resize (`Fit page`). Fit width / fit height / 100% are toolbar actions; zoom in/out (also Ctrl+mouse wheel) switches to manual zoom. Scrollbars appear when the scaled page is larger than the canvas. The toolbar shows the current zoom and **Page N of M**.

Desktop uses an SWT `Canvas` + `SwtUniversalImageSvg`. Hop Web uses an SWT
`Browser` that fills the viewer. Fit width/height/page are computed from the
iframe inner size with a 24px inset and clamped so neither axis exceeds the pane
(native RAP scrollbars would shrink the client area and flicker at ~200%). The
SVG is sized to those CSS pixels (no `transform:scale`). `overflow:auto` is only
for manual zoom past the pane. Live refresh replaces the SVG in place
(RAP `evaluate` / `execute`) and must not call `Browser.setText` again. Still no
HTTP server.

## Metadata editors

Hop's metadata perspective loads `<TypeName>Editor` from the same package as
each `@HopMetadata` type. This module supplies those editors (presentation,
connector, theme, database connection, pictorial series, security, audit,
server settings, …). `HAnnotatedMetadataEditor` / `HMetadataForm` build the
SWT form from `@HopMetadataProperty` and `@HWidgetElement` so nested
components, connector plugins, colors, fonts, lists, and maps are editable
without a parallel `@GuiWidgetElement` layer.

## Demo

```bash
mvn -q exec:java
```

## Hop embed

When shipped inside a Hop plugin, depend with `-Phop-embed` (or exclude
`org.apache.hop:*`) so hop-core / hop-ui are not duplicated on the plugin
classpath.
