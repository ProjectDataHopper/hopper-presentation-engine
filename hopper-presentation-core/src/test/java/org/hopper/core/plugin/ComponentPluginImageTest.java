package org.hopper.core.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.type.HComponentPluginType;

/** Every registered component with a non-empty image path must resolve on the classpath. */
class ComponentPluginImageTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void componentPluginsDeclareLoadableImages() {
    PluginRegistry registry = PluginRegistry.getInstance();
    List<IPlugin> plugins = registry.getPlugins(HComponentPluginType.class);
    assertFalse(plugins.isEmpty(), "expected registered component plugins");

    List<String> failures = new ArrayList<>();
    int withImage = 0;
    for (IPlugin plugin : plugins) {
      String id = plugin.getIds()[0];
      String image = plugin.getImageFile();
      if (image == null || image.isBlank()) {
        failures.add(id + ": missing image annotation");
        continue;
      }
      withImage++;
      try {
        ClassLoader cl = registry.getClassLoader(plugin);
        assertNotNull(cl, id + " classloader");
        try (InputStream in = cl.getResourceAsStream(image)) {
          if (in == null) {
            failures.add(id + ": resource not found: " + image);
          }
        }
      } catch (Exception e) {
        failures.add(id + ": " + e.getMessage());
      }
    }
    assertTrue(withImage > 0, "expected at least one component with image");
    if (!failures.isEmpty()) {
      fail("Component image problems:\n" + String.join("\n", failures));
    }
  }

  @Test
  void defaultComponentImageExists() {
    try (InputStream in =
        getClass()
            .getClassLoader()
            .getResourceAsStream(HPluginInfoRest.DEFAULT_COMPONENT_IMAGE)) {
      assertNotNull(
          in, "default component image missing: " + HPluginInfoRest.DEFAULT_COMPONENT_IMAGE);
    } catch (Exception e) {
      fail(e);
    }
  }
}
