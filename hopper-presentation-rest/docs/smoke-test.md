# hopper-presentation-rest smoke test

End-to-end check that the REST layer can load metadata and return **server-side SVG** from hopper-presentation-core on Java 21 / Hop 2.18.1.

## Prerequisites

1. Install hopper-presentation-core:

   ```bash
   cd ../hopper-presentation-core && mvn clean install -DskipTests
   ```

2. Package hopper-presentation-rest (optional; `jetty:run` builds as needed):

   ```bash
   cd ../hopper-presentation-rest && mvn clean package -DskipTests
   ```

3. Test metadata lives under `src/test/resources/metadata/` and is referenced from  
   `src/test/resources/hopper-presentation.properties` (`metadata.path`).

## Start the server

Config path is the **system property** `HOPPER_REST_CONFIG_PATH` (directory containing `hopper-presentation.properties`), not Maven `-DCONFIG_PATH`.

```bash
# From monorepo root
export HOPPER_REST_CONFIG_PATH="$PWD/hopper-presentation-rest/src/test/resources"
mvn -pl hopper-presentation-rest -am jetty:run -DHOPPER_REST_CONFIG_PATH="$HOPPER_REST_CONFIG_PATH"
```

Expected log lines:

- `Found configuration file: .../hopper-presentation.properties`
- `Found 15 metadata types.` (count may vary)
- `Started ServerConnector...{0.0.0.0:8080}`

Base URL:

| Piece | Value |
|-------|--------|
| Context | `/hopper` |
| JAX-RS application path | `/api` (`@ApplicationPath("api")`) |
| API root | **`http://localhost:8080/hopper/api`** |

> Paths such as `/hopper/render/main` **without** `/api` return 404.

## Manual checks

```bash
BASE=http://localhost:8080/hopper/api

# Main HTML shell (opens list-presentations in JS)
curl -sS -o /tmp/hopper-main.html -w "%{http_code}\n" "$BASE/render/main/"

# Metadata
curl -sS "$BASE/metadata/types"
curl -sS "$BASE/metadata/presentations/"

# Render a presentation (returns UUID as plain text)
RID=$(curl -sS -X POST -H 'Content-Type: application/json' \
  -d '{"presentationName":"list-presentations","parameters":[],"reload":true}' \
  "$BASE/render/presentation")
echo "renderId=$RID"

# Page count + SVG
curl -sS "$BASE/render/info/pages/$RID"
curl -sS -o /tmp/hopper-page.svg "$BASE/render/page/$RID/SVG/0/"
head -c 200 /tmp/hopper-page.svg; echo
grep -q '<svg' /tmp/hopper-page.svg && echo PASS || echo FAIL
```

### Expected results (smoke, 2026-07-22)

| Check | Expected |
|-------|----------|
| Jetty + HEnvironment | Starts; metadata types loaded |
| `GET .../render/main/` | **200**, HTML with jQuery + `API_BASE = '/hopper/api/'` |
| `GET .../metadata/types` | **200**, JSON array including `presentation`, `theme`, `hopper-database-connection` |
| `GET .../metadata/presentations/` | **200**, list of presentation names |
| `POST .../render/presentation` (`list-presentations`) | **200**, UUID render id |
| `GET .../render/info/pages/{id}` | **200**, e.g. `1` |
| `GET .../render/page/{id}/SVG/0/` | **200**, non-trivial SVG containing `<svg` |

### Known non-failures

- **`SteelWheels Customer Table`** (and similar SQL presentations) return **500** if the H2 file/DB referenced by metadata is empty or missing tables (`CUSTOMERS not found`). That is **test data setup**, not a platform regression. Populate SteelWheels H2 or point `hopper-database-connection` metadata at a loaded database before expecting those to render.

### Connector schema / studio (baseline)

Design contract for the future **connector data studio** (input samples / settings / output samples, Apply = preview): see **[connector-studio.md](./connector-studio.md)** and hopper-presentation-core `docs/connectors.md` (*Connector studio preview*).

Exists today (no sample rows yet):

```bash
# Output row layout for a saved connector (schema only)
curl -sS -X POST -H 'Content-Type: application/json' \
  -d '{"connectorName":"Sample Data"}' \
  "$BASE/render/connector/describe/"

# Generated form HTML for a plugin type
curl -sS -o /tmp/hopper-sql-form.html -w "%{http_code}\n" \
  "$BASE/edit/connector/SqlConnector/"

# Plugin catalog
curl -sS "$BASE/plugins/connectors"
```

| Check | Expected (today) |
|-------|------------------|
| `POST .../render/connector/describe/` (`Sample Data`) | **200**, JSON array of value meta (`name`, `type`, …) |
| `GET .../edit/connector/SqlConnector/` | **200**, HTML with Apply/Close and `connectorSaveScript` |
| `POST .../edit/connector/preview/` | **200**, `{ ok, maxRows, input?, output, error }` with sample rows |
| `GET .../metadata/summary/{key}/` | **200**, `[{ name, description, virtualPath, … }, …]` (e.g. `presentation`, `connector`, `theme`, `hopper-database-connection`) |
| `GET .../metadata/connectors/summary/` | **200**, same as `summary/connector/` (`pluginId` included) |

```bash
# Connector studio preview (inline JSON; does not save)
curl -sS -X POST -H 'Content-Type: application/json' \
  -d '{"maxRows":5,"hopperConnectorJson":"{\"name\":\"preview-sample\",\"connector\":{\"SampleDataConnector\":{\"pluginId\":\"SampleDataConnector\",\"rowCount\":10}}}"}' \
  "$BASE/edit/connector/preview/" | head -c 400; echo

# Metadata summaries (icons / types / virtual paths)
curl -sS "$BASE/metadata/summary/connector/" | head -c 300; echo
curl -sS "$BASE/metadata/summary/presentation/" | head -c 300; echo
```

**UI polish checks (manual):** edit mode → connectors → open a **Sort** / **Select** / **Chain** with a source → changing **Source connector** should immediately show the **Input** pane with sample rows; **Apply** refreshes output; sample-size dropdown limits rows.

### Relative layout (multi-page tables)

**Bug fixed:** siblings of multi-page tables (e.g. `products` / **Bar Chart** left = right of **ProductsTable**) were laid out only on the **last** overflow page. They now use the table’s **first-part** geometry and place on **page 1**.

Manual check:

1. Open `products` in edit mode → page 1 should show **ProductsTable** and **Bar Chart** to its right (`x ≈ table.right`).
2. Open Bar Chart properties → expand **Layout options** → **Layout result** at the bottom shows resolved box, pages, attachment summaries, and multi-page reference hints. Change layout and **Apply** → Layout result refreshes from the new layout.
3. `combo-test` still shows Crosstab + charts on a single page.

### Presentation properties (interactions + parameter mappings)

Chrome: in **edit** and **view**, the presentation name appears **top-right** of the toolbar strip. In edit mode it is a link (underline on hover); click opens the properties side panel.

| Check | Expected |
|-------|----------|
| Title bar | Name visible top-right; edit: clickable; view: plain text |
| Properties load | `GET .../metadata/presentation/{name}` fills name, description, default theme, themes, interactions, parameter mappings |
| Header/footer fields | Loaded/saved via `.../edit/presentation/{name}/header-footer/` |
| Interactions list | Cards with summary like demo `list-executions` (click cell → OPEN_PRESENTATION + param) |
| Component Interactions section | Open a table/chart in the property form → **Interactions** lists rules for that component only; Add/Edit returns to the form |
| Parameter mappings | Cards for connector + field→parameter rows (demo: `execution-details`) |
| Save | `POST .../metadata/presentation/` then soft re-render; rename deletes old name and navigates |

Manual UI path:

1. Open `list-executions` in **edit** mode.
2. Click the presentation name (top-right) → properties panel.
3. Confirm two interactions (executionId → `execution-details` / EXECUTION_ID; name → `execution-trend` / OBJECT_NAME).
4. Open `execution-details` → confirm parameter mapping for `hop-execution-details`.
5. Add or edit a mapping / interaction → **Save** → soft reload; verify in **view** that drill-down still works.
6. **Table drill-down** preset: creates a Cell/ComponentItem interaction pre-filled with the first table component; pick dimension column checkboxes + target presentation.
7. Parameter mapping field names: combo from connector `describe` (falls back to text).
8. Themes: **Add** / **Remove** embedded themes; Close with unsaved edits asks to discard.
9. **Dimension params:** open a chart/table cell interaction that uses `dimensionParameters` (e.g. maritime `company_name` → `COMPANY_NAME`); in **view**, click/hover different categories — popup and open presentation must filter by the hit, not only the parameter default.
10. **Table form:** blank column width = auto (header + body); **All columns** appends missing connector fields; preview uses natural column widths.

```bash
# Load presentation metadata (includes interactions + parameterMappings)
curl -sS "$BASE/metadata/presentation/list-executions" | head -c 400; echo
curl -sS "$BASE/metadata/presentation/execution-details" | head -c 400; echo

# List presentation names for interaction target pickers
curl -sS "$BASE/metadata/list/presentation/"
```

## Stop the server

Ctrl+C in the Jetty terminal, or kill the `mvn jetty:run` process.

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| All APIs 404 under `/hopper/...` | Missing `/api` segment |
| Config not found / 0 metadata | Wrong `HOPPER_REST_CONFIG_PATH` or `metadata.path` in properties |
| Init fails reading Hop jars / jandex | hopper-presentation-core/hop versions mismatch (need Hop 2.18.1 + Jandex 3.5.3) |
| SVG empty or error JSON | Presentation missing connectors/theme, or SQL data unavailable |
