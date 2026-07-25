package org.hopper.metadata.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.csv.HCsvConnector;

class HMetadataCodecTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void parseHopPolymorphicPresentation() throws Exception {
    String json =
        """
        {
          "name": "poly",
          "pages": [{
            "components": [{
              "name": "title",
              "layout": {
                "left": { "offset": 0, "alignment": "LEFT" },
                "top": { "offset": 0, "alignment": "TOP" }
              },
              "component": {
                "HLabelComponent": {
                  "pluginId": "HLabelComponent",
                  "label": "Hello poly"
                }
              }
            }]
          }]
        }
        """;
    HPresentation p = HMetadataCodec.parsePresentation(json);
    assertEquals("poly", p.getName());
    assertEquals(1, p.getPages().get(0).getComponents().size());
    HLabelComponent label =
        (HLabelComponent) p.getPages().get(0).getComponents().get(0).getComponent();
    assertEquals("Hello poly", label.getLabel());
  }

  @Test
  void parseFlatPresentation() throws Exception {
    String json =
        """
        {
          "name": "flat",
          "pages": [{
            "components": [{
              "name": "title",
              "component": {
                "pluginId": "HLabelComponent",
                "label": "Hello flat"
              }
            }]
          }]
        }
        """;
    HPresentation p = HMetadataCodec.parsePresentation(json);
    HLabelComponent label =
        (HLabelComponent) p.getPages().get(0).getComponents().get(0).getComponent();
    assertEquals("Hello flat", label.getLabel());
  }

  @Test
  void parseHopConnector() throws Exception {
    String json =
        """
        {
          "name": "c1",
          "connector": {
            "CsvConnector": {
              "pluginId": "CsvConnector",
              "filename": "x.csv",
              "headerPresent": true
            }
          }
        }
        """;
    HConnector c = HMetadataCodec.parseConnector(json);
    assertEquals("c1", c.getName());
    assertTrue(c.getConnector() instanceof HCsvConnector);
    assertEquals("x.csv", ((HCsvConnector) c.getConnector()).getFilename());
  }

  @Test
  void templatesParse() throws Exception {
    for (String name :
        new String[] {
          "presentation-title-table.json",
          "presentation-text-block.json",
          "connector-csv.json"
        }) {
      String json =
          new String(
              getClass()
                  .getClassLoader()
                  .getResourceAsStream("ai-templates/" + name)
                  .readAllBytes(),
              StandardCharsets.UTF_8);
      if (name.startsWith("connector")) {
        assertNotNull(HMetadataCodec.parseConnector(json).getConnector());
      } else {
        assertNotNull(HMetadataCodec.parsePresentation(json).getName());
      }
    }
  }

  @Test
  void hopRoundTripLabel() throws Exception {
    String json =
        """
        {
          "name": "rt",
          "pages": [{
            "components": [{
              "name": "t",
              "component": { "pluginId": "HLabelComponent", "label": "X" }
            }]
          }]
        }
        """;
    HPresentation p = HMetadataCodec.parsePresentation(json);
    String hop = HMetadataCodec.toHopJson(p, new MemoryMetadataProvider());
    assertTrue(hop.contains("HLabelComponent") || hop.contains("pluginId"));
    HPresentation again = HMetadataCodec.parsePresentation(hop);
    assertEquals("rt", again.getName());
  }
}
