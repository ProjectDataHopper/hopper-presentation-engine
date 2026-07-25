package org.hopper.metadata.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;

class HJsonSchemaExporterTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void exportPresentationAndConnectorSchemas() throws Exception {
    HJsonSchemaExporter exporter = new HJsonSchemaExporter();
    String presentation = exporter.presentationSchemaJson();
    assertTrue(presentation.contains("pages"));
    assertTrue(presentation.contains("pluginId") || presentation.contains("component"));

    String connector = exporter.connectorSchemaJson();
    assertTrue(connector.contains("connector"));

    Path out = Path.of("target/ai-schemas-test");
    exporter.writeToDirectory(out);
    assertTrue(Files.exists(out.resolve("presentation.schema.json")));
    assertTrue(Files.exists(out.resolve("connector.schema.json")));
  }
}
