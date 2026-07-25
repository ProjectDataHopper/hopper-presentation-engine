package org.hopper.core.gui.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.component.types.chart.HBarChartComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;

class HGuiRegistryTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void scansLabelComponentWidgets() {
    HGuiRegistry registry = HGuiRegistry.getInstance();
    assertTrue(registry.isScanned());

    Map<String, List<HWidgetElements>> byParent =
        registry.getElementsByParent(HLabelComponent.class);
    assertFalse(byParent.isEmpty());

    List<HWidgetElements> pluginFields =
        byParent.getOrDefault(HGuiFormConstants.PARENT_PLUGIN, List.of());
    assertTrue(pluginFields.stream().anyMatch(w -> "label".equals(w.getFieldName())));
    assertTrue(pluginFields.stream().anyMatch(w -> "underline".equals(w.getFieldName())));

    List<HWidgetElements> baseFields =
        byParent.getOrDefault(HGuiFormConstants.PARENT_BASE, List.of());
    assertTrue(baseFields.stream().anyMatch(w -> "themeName".equals(w.getFieldName())));
  }

  @Test
  void labelIgnoresSourceConnectorWithMarkerSemantics() {
    List<HWidgetElements> baseFields =
        HGuiRegistry.getInstance()
            .getElementsByParent(HLabelComponent.class)
            .getOrDefault(HGuiFormConstants.PARENT_BASE, List.of());

    Optional<HWidgetElements> source =
        baseFields.stream().filter(w -> "sourceConnectorName".equals(w.getId())).findFirst();
    assertTrue(source.isPresent(), "ignored marker (or field) should remain registered by id");
    assertTrue(source.get().isIgnored(), "subclass ignored=true must suppress base widget");
    assertTrue(
        baseFields.stream()
            .filter(w -> "themeName".equals(w.getId()))
            .findFirst()
            .map(w -> !w.isIgnored())
            .orElse(false));
  }

  @Test
  void tableKeepsSourceConnectorVisible() {
    List<HWidgetElements> baseFields =
        HGuiRegistry.getInstance()
            .getElementsByParent(HTableComponent.class)
            .getOrDefault(HGuiFormConstants.PARENT_BASE, List.of());
    Optional<HWidgetElements> source =
        baseFields.stream().filter(w -> "sourceConnectorName".equals(w.getId())).findFirst();
    assertTrue(source.isPresent());
    assertFalse(source.get().isIgnored());
  }

  @Test
  void barChartIgnoresDotSizeAndLineWidth() {
    List<HWidgetElements> pluginFields =
        HGuiRegistry.getInstance()
            .getElementsByParent(HBarChartComponent.class)
            .getOrDefault(HGuiFormConstants.PARENT_PLUGIN, List.of());
    assertTrue(
        pluginFields.stream()
            .filter(w -> "dotSize".equals(w.getId()))
            .findFirst()
            .map(HWidgetElements::isIgnored)
            .orElse(false));
    assertTrue(
        pluginFields.stream()
            .filter(w -> "lineWidth".equals(w.getId()))
            .findFirst()
            .map(HWidgetElements::isIgnored)
            .orElse(false));
  }
}
