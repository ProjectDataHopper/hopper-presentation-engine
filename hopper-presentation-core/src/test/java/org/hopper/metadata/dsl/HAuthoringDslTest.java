package org.hopper.metadata.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.metadata.validate.HMetadataValidator;
import org.hopper.metadata.validate.ValidateOptions;
import org.hopper.metadata.validate.ValidationReport;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.csv.HCsvConnector;

class HAuthoringDslTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void compilePresentation_labelAndTable() throws Exception {
    String dsl =
        """
        {
          "kind": "presentation",
          "name": "dsl-demo",
          "theme": "Default",
          "pages": [{
            "components": [
              {
                "name": "title",
                "type": "label",
                "text": "Hello",
                "place": { "recipe": "topLeft", "offset": [12, 12] }
              },
              {
                "name": "runs",
                "type": "table",
                "connector": "ops-runs-sample",
                "header": true,
                "columns": [
                  { "column": "runId", "header": "Run ID", "width": 100 }
                ],
                "place": { "recipe": "under", "of": "title", "gap": 12, "spanWidth": true }
              }
            ]
          }]
        }
        """;
    HPresentation p = HAuthoringDsl.compilePresentation(dsl);
    assertEquals("dsl-demo", p.getName());
    assertEquals(2, p.getPages().get(0).getComponents().size());
    assertInstanceOf(
        HLabelComponent.class, p.getPages().get(0).getComponents().get(0).getComponent());
    assertInstanceOf(
        HTableComponent.class, p.getPages().get(0).getComponents().get(1).getComponent());
    HTableComponent table =
        (HTableComponent) p.getPages().get(0).getComponents().get(1).getComponent();
    assertEquals("ops-runs-sample", table.getSourceConnectorName());
    assertEquals(1, table.getColumnSelection().size());

    ValidationReport report =
        new HMetadataValidator().validatePresentation(p, ValidateOptions.builder().build());
    assertTrue(report.isOk(), report.errors().toString());
  }

  @Test
  void compileConnector_csv() throws Exception {
    String dsl =
        """
        {
          "kind": "connector",
          "name": "c1",
          "type": "csv",
          "filename": "x.csv",
          "headerPresent": true,
          "fields": [ { "name": "id", "type": "String" } ]
        }
        """;
    HConnector c = HAuthoringDsl.compileConnector(dsl);
    assertEquals("c1", c.getName());
    assertInstanceOf(HCsvConnector.class, c.getConnector());
    assertEquals("x.csv", ((HCsvConnector) c.getConnector()).getFilename());
  }
}
