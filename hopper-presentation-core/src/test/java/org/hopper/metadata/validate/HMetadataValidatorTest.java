package org.hopper.metadata.validate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;

class HMetadataValidatorTest {

  private HMetadataValidator validator;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    validator = new HMetadataValidator();
  }

  @Test
  void goodTemplate_isOk() throws Exception {
    String json = resource("ai-templates/presentation-text-block.json");
    ValidationReport report =
        validator.validatePresentationJson(json, ValidateOptions.builder().build());
    assertTrue(report.isOk(), report.errors().toString());
  }

  @Test
  void missingAttachment_errors() {
    String json =
        """
        {
          "name": "bad",
          "pages": [{
            "components": [{
              "name": "a",
              "layout": {
                "top": {
                  "componentName": "does-not-exist",
                  "offset": 0,
                  "alignment": "BOTTOM"
                }
              },
              "component": { "pluginId": "HLabelComponent", "label": "x" }
            }]
          }]
        }
        """;
    ValidationReport report =
        validator.validatePresentationJson(json, ValidateOptions.builder().build());
    assertFalse(report.isOk());
    assertTrue(
        report.errors().stream()
            .anyMatch(e -> HMetadataValidator.ATTACHMENT_MISSING.equals(e.getCode())));
  }

  @Test
  void unknownPlugin_errors() {
    String json =
        """
        {
          "name": "bad",
          "pages": [{
            "components": [{
              "name": "a",
              "component": { "pluginId": "NotARealPlugin", "label": "x" }
            }]
          }]
        }
        """;
    ValidationReport report =
        validator.validatePresentationJson(json, ValidateOptions.builder().build());
    assertFalse(report.isOk());
    assertTrue(
        report.errors().stream()
            .anyMatch(e -> HMetadataValidator.UNKNOWN_PLUGIN.equals(e.getCode())
                || HMetadataValidator.PARSE_ERROR.equals(e.getCode())));
  }

  @Test
  void attachmentCycle_errors() {
    String json =
        """
        {
          "name": "cycle",
          "pages": [{
            "components": [
              {
                "name": "a",
                "layout": {
                  "top": { "componentName": "b", "offset": 0, "alignment": "BOTTOM" }
                },
                "component": { "pluginId": "HLabelComponent", "label": "A" }
              },
              {
                "name": "b",
                "layout": {
                  "top": { "componentName": "a", "offset": 0, "alignment": "BOTTOM" }
                },
                "component": { "pluginId": "HLabelComponent", "label": "B" }
              }
            ]
          }]
        }
        """;
    ValidationReport report =
        validator.validatePresentationJson(json, ValidateOptions.builder().build());
    assertFalse(report.isOk());
    assertTrue(
        report.errors().stream()
            .anyMatch(e -> HMetadataValidator.ATTACHMENT_CYCLE.equals(e.getCode())));
  }

  private static String resource(String path) throws Exception {
    try (var in = HMetadataValidatorTest.class.getClassLoader().getResourceAsStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
