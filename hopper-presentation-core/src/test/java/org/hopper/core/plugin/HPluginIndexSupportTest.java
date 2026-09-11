package org.hopper.core.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.connector.type.HConnectorPluginType;

class HPluginIndexSupportTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  @Test
  void hopperPresentationJarIndexesAreAccepted() throws Exception {
    assertTrue(
        HPluginIndexSupport.isHopperPresentationIndex(
            URI.create("jar:file:/tmp/hopper-presentation-core-1.0.0-SNAPSHOT.jar!/META-INF/jandex.idx")
                .toURL()));
    assertTrue(
        HPluginIndexSupport.isHopperPresentationIndex(
            URI.create(
                    "file:/home/matt/hopper-presentation-engine/hopper-presentation-core/target/classes/META-INF/jandex.idx")
                .toURL()));
    assertFalse(
        HPluginIndexSupport.isHopperPresentationIndex(
            URI.create("jar:file:/opt/hop/lib/core/hop-core-2.19.0.jar!/META-INF/jandex.idx")
                .toURL()));
    assertFalse(
        HPluginIndexSupport.isHopperPresentationIndex(
            URI.create(
                    "jar:file:/opt/hop/plugins/misc/hopper-edw/hopper-edw-0.11.0-SNAPSHOT.jar!/META-INF/jandex.idx")
                .toURL()));
  }

  @Test
  void labelComponentIsRegistered() {
    PluginRegistry registry = PluginRegistry.getInstance();
    IPlugin plugin = registry.getPlugin(HComponentPluginType.class, "HLabelComponent");
    assertNotNull(plugin, "HLabelComponent must be in the plugin registry after HEnvironment.init()");
    assertTrue(plugin.getClassMap().containsValue(HLabelComponent.class.getName()));
  }

  @Test
  void sampleDataConnectorIsRegistered() {
    IPlugin plugin =
        PluginRegistry.getInstance().getPlugin(HConnectorPluginType.class, "SampleDataConnector");
    assertNotNull(plugin);
  }

  @Test
  void registerHopperGuiElementsDoesNotWalkEveryHopperGuiPlugin() {
    HPluginIndexSupport.registerHopperGuiElements(HPluginIndexSupport.class.getClassLoader());
  }
}
