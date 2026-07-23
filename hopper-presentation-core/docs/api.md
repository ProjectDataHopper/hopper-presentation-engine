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

- `HParameter` values are applied as variables on the presentation data context.
- `HParameterMapping` can load values from a connector field list before layout.

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
