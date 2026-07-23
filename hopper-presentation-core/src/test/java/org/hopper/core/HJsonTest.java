package org.hopper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;

class HJsonTest {

  @Test
  void roundTripsPresentationWithoutHopRuntimeFields() throws Exception {
    HPresentation presentation = new HPresentation();
    presentation.setName("demo");
    presentation.setDescription("json round trip");
    presentation.setDefaultThemeName(Constants.DEFAULT_THEME_NAME);

    ObjectMapper mapper = HJson.createMapper();
    String json = mapper.writeValueAsString(presentation);
    assertFalse(json.contains("\"fullName\""), "fullName should be ignored in JSON");
    assertFalse(json.contains("metadataProviderName"));
    assertFalse(json.contains("\"themes\""), "themes are no longer embedded on presentations");
    assertFalse(
        json.contains("\"connectors\""), "connectors are no longer embedded on presentations");

    HPresentation restored = mapper.readValue(json, HPresentation.class);
    assertNotNull(restored);
    assertEquals("demo", restored.getName());
    assertEquals(Constants.DEFAULT_THEME_NAME, restored.getDefaultThemeName());
  }

  @Test
  void ignoresLegacyEmbeddedThemesAndConnectors() throws Exception {
    String legacy =
        "{"
            + "\"name\":\"legacy\","
            + "\"defaultThemeName\":\"Default\","
            + "\"themes\":[{\"name\":\"Default\"}],"
            + "\"connectors\":[{\"name\":\"Sample Data\"}],"
            + "\"pages\":[]"
            + "}";
    HPresentation restored = HJson.createMapper().readValue(legacy, HPresentation.class);
    assertEquals("legacy", restored.getName());
    assertEquals("Default", restored.getDefaultThemeName());
  }
}
