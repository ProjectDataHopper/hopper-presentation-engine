package org.hopper.core.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HPluginIdsTest {

  @Test
  void mapsLegacyLeanComponentIds() {
    assertEquals("HBarChartComponent", HPluginIds.resolve("LeanBarChartComponent"));
    assertEquals("HTableComponent", HPluginIds.resolve("LeanTableComponent"));
    assertEquals("HCrosstabComponent", HPluginIds.resolve("LeanCrosstabComponent"));
  }

  @Test
  void keepsHopperIds() {
    assertEquals("HBarChartComponent", HPluginIds.resolve("HBarChartComponent"));
    assertEquals("SqlConnector", HPluginIds.resolve("SqlConnector"));
  }

  @Test
  void mapsLeanPrefixedConnectors() {
    assertEquals("HRestConnector", HPluginIds.resolve("LeanRestConnector"));
    assertEquals("HListConnector", HPluginIds.resolve("LeanListConnector"));
  }

  @Test
  void nullSafe() {
    assertNull(HPluginIds.resolve(null));
    assertEquals("", HPluginIds.resolve(""));
  }
}
