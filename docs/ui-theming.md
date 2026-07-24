# UI theming (light / dark)

## How it works

1. **`hopper-theme-tokens.css`** defines semantic CSS variables for light and dark.
2. **`hopper-theme.js`** (and a small FOUC script in each HTML shell) sets
   `data-theme="light"|dark"` on `<html>` / `<body>` from the user preference
   (`localStorage` key `hopperColorMode`: `system` | `light` | `dark`).
3. Component stylesheets (`hopper-presentation.css`, `admin/admin.css`) use only
   `var(--hopper-*)` — they must not invent hex colors for chrome.

Presentation **SVG** colors are separate: server-side `HTheme` resolved by
request `colorMode` (`defaultThemeName` / `darkThemeName` / derived dark).

## Changing the look

| Want to… | Edit |
|----------|------|
| Shift dark background | `--hopper-bg` in the dark block of `hopper-theme-tokens.css` |
| Restyle all inputs | `--hopper-input-bg`, `--hopper-input-border` |
| Restyle all tables | `--hopper-table-header-bg`, `--hopper-table-border`, … |
| Status banners | `--hopper-danger-*`, `--hopper-success-*`, `--hopper-warning-*` |

## Adding a new panel or control

```css
.my-panel {
  background: var(--hopper-panel-bg);
  color: var(--hopper-text);
  border: 1px solid var(--hopper-border);
}
```

Do **not** add:

- Hard-coded `#…` / `rgb(…)` in component CSS
- `html[data-theme="dark"] .my-panel { … }` forks (use tokens instead)
- Inline `style="color:…; background:…"` in Java/JS HTML generators

## Shared primitives

Defined in `hopper-theme-tokens.css`:

- `.hopper-fieldset` — themed fieldset border
- `.hopper-table` — themed data table
- `.hopper-icon-invert` — **opt-in only** for legacy navy mono plugin icons without a dark asset

## Dual static icons (ahead-of-time)

Monochrome chrome icons ship as two files:

| Mode | Path |
|------|------|
| Light | `WEB-INF/static/images/<name>.svg` |
| Dark | `WEB-INF/static/images/dark/<name>.svg` |

Generate dark variants after editing light sources:

```bash
python3 scripts/generate-dark-icons.py
```

Commit both light and dark files. Runtime uses `uiIconUrl("name.svg")` /
`data-ui-icon="name.svg"` (`hopper-theme.js`) — **no** canvas or CSS invert for
these icons.

**Do not** invert multi-color logos (`hopper-presentation-logo.svg`, etc.).
**Do not** add new `filter: invert` rules for app chrome icons.

### Adding a toolbar / list icon

1. Add the light SVG under `static/images/`
2. Run `scripts/generate-dark-icons.py`
3. Reference via `uiIconUrl("my-icon.svg")` or `data-ui-icon="my-icon.svg"`
4. Theme toggle calls `refreshUiIcons()` automatically

Presentation page SVG/PNG stays server `colorMode` + soft-reload (data-driven;
not dual static files).

## Shell checklist

Every HTML entry point should:

1. Run the FOUC script before CSS  
2. Link `hopper-theme-tokens.css` first  
3. Link `hopper-presentation.css` (and `admin/admin.css` for admin)  
4. Load `hopper-theme.js`
