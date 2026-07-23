package org.hopper.core.gui.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.presentation.component.types.label.HLabelComponent;

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
}
