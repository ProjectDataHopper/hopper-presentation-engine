# Hopper Presentation Engine Public API

Maven coordinates:

```xml
<dependency>
  <groupId>org.hopper</groupId>
  <artifactId>hopper-presentation-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Java 21**, **Apache Hop 2.18.1**.

## Bootstrap

```java
HEnvironment.init();
```

Idempotent and synchronized. Required before plugin lookup, metadata serializers, and most connectors.

## Load a presentation

**From JSON:**

```java
HPresentation presentation = HPresentation.fromJsonString(json);
String jsonOut = presentation.toJsonString(true); // pretty
```

Uses `HJson.createMapper()` (ignores Hop runtime fields such as `fullName`).

**From Hop metadata:**

```java
IHopMetadataProvider provider = ...; // e.g. MemoryMetadataProvider or JSON folder provider
HPresentation p = provider.getSerializer(HPresentation.class).load("MyPresentation");
```

## Layout and render

```java
ILoggingObject parent = new LoggingObject("app");
IRenderContext renderContext = new PresentationRenderContext(presentation, provider);
List<HParameter> parameters = List.of(new HParameter("REGION", "EMEA"));

HLayoutResults results =
    presentation.doLayout(parent, renderContext, provider, parameters);

ILogChannel log = presentation.render(results, provider);

for (HRenderPage page : results.getRenderPages()) {
  String svgXml = page.getSvgXml(); // or page.getGc() for Batik API
}
```

### Parameters

Presentations declare **parameter definitions** (Hop pipeline/workflow style) on
`HPresentation.parameters`: `name`, `description`, `defaultValue`. These drive editor lists,
interaction mapping pickers, and future prompts.

Runtime values use `HParameter` (`parameterName` / `parameterValue`) when calling `doLayout`.

**Variable hierarchy at layout** (later wins):

1. **System variables** — server admin “System variables” via `HGlobalVariables` (copied into each
   `PresentationDataContext`).
2. **Presentation parameter defaults** — from `HParameterDefinition.defaultValue` when the name is
   not supplied by the caller and the variable is still empty.
3. **Parameter mappings** — optional mapping-level `defaultValue`, then connector field values
   (multi-row with a blank join separator is skipped so `${PARAM}` can remain).
4. **Request / interaction parameters** — `List<HParameter>` to `doLayout` always win.

- Labels and SQL can use `${PARAM_NAME}` once a default or request value is set.
- **Interaction actions** (`HInteractionAction`): optional `valueParameter` sets a parameter from
  the clicked value; optional `dimensionParameters` maps context dimension columns
  (`DrawnContext.dimensionValues`, e.g. crosstab region/year) to additional parameters.

## Connectors programmatically

```java
HSampleDataConnector sample = new HSampleDataConnector(100);
HConnector connector = new HConnector("rows", sample);
presentation.getConnectors().add(connector);

// Or collect rows (all rows — not for UI preview):
PresentationDataContext ctx = new PresentationDataContext(presentation, provider);
List<RowMetaAndData> rows = connector.retrieveRows(ctx);
```

For **limited sample previews** (connector studio), see `docs/connectors.md` → *Connector studio preview*. Do not use unbounded `retrieveRows` for browser Apply/preview.

## Themes

```java
HTheme theme = HTheme.getDefault();
presentation.getThemes().add(theme);
presentation.setDefaultThemeName(theme.getName());
```

Components fall back to the presentation default theme when `themeName` is unset.

## Database connections

```java
HDatabaseConnection db = new HDatabaseConnection(
    "steelwheels", "H2", "localhost", "0", "/path/to/db", "sa", "");
provider.getSerializer(HDatabaseConnection.class).save(db);

HSqlConnector sql = new HSqlConnector("steelwheels", "SELECT * FROM customers");
```

Database type codes match Hop (`H2`, `MYSQL`, `POSTGRESQL`, …). The corresponding Hop database plugin must be on the classpath (e.g. `hop-databases-h2`).

## Embedding vs Hop plugin

| Mode | Hop dependency | Notes |
|------|----------------|-------|
| Standalone library | compile (default) | Apps / hopper-presentation-rest pull hop-core transitively |
| Inside Hop | provided profile (future) | Avoid duplicate hop-core on plugin classpath |

## AI / automated authoring

For agents generating connectors and presentations:

- **Docs & templates:** repository [docs/ai/](../../docs/ai/README.md)
- **Parse both wire shapes:** `HMetadataCodec.parsePresentation(json)` / `parseConnector(json)`
- **Validate:** `new HMetadataValidator().validatePresentationJson(json, options)`
- **DSL → model:** `HAuthoringDsl.compilePresentation(dslJson)`
- **REST:** `GET /hopper/api/ai/context`, `POST /hopper/api/ai/validate/presentation`, `POST /hopper/api/ai/compile/presentation`

Canonical **on-disk** plugin shape is Hop polymorphic (`"component": { "HLabelComponent": { … } }`); flat `pluginId` is also accepted.

## Publishing

See **[publishing.md](publishing.md)** for Nexus `hopper` at `repository.data-hopper.com`.

```bash
# credentials: ~/.m2/settings.xml server id "hopper"
mvn clean deploy
```

## Package overview

| Package | Contents |
|---------|----------|
| `org.hopper.core` | Environment, geometry, colors, JSON, exceptions |
| `org.hopper.presentation` | Presentation model, layout, pages, themes |
| `org.hopper.presentation.component` | Components and plugin types |
| `org.hopper.presentation.connector` | Connectors and plugin types |
| `org.hopper.render` | Render contexts and PDF helpers |
