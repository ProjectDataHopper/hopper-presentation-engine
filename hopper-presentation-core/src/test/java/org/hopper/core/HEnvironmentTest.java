package org.hopper.core;

import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.HConnectorPluginType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HEnvironmentTest {

  @BeforeEach
  public void before() throws Exception {

    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    IVariables variables = Variables.getADefaultVariableSpace();

    // Load all plugins, initialize environment
    //
    HEnvironment.init();
  }

  @Test
  public void testInit() throws Exception {
    PluginRegistry registry = PluginRegistry.getInstance();

    // Check Component plugin type...
    //
    IPluginType hopperComponentPluginType = registry.getPluginType(HComponentPluginType.class);
    assertNotNull(hopperComponentPluginType, "Component plugin type not found");
    IPlugin hopperLabelComponent =
        registry.findPluginWithId(HComponentPluginType.class, "HLabelComponent");
    assertNotNull(hopperLabelComponent, "Label component not found");

    List<IPlugin> componentPlugins = registry.getPlugins(HComponentPluginType.class);
    assertTrue(!componentPlugins.isEmpty(), "Plugins list empty");

    // Check connector plugin type...
    //
    IPluginType hopperConnectorPluginType = registry.getPluginType(HConnectorPluginType.class);
    assertNotNull(hopperConnectorPluginType, "Data connector plugin type not found");
    IPlugin sampleDataConnector =
        registry.findPluginWithId(HConnectorPluginType.class, "SampleDataConnector");
    assertNotNull(sampleDataConnector, "Sample data connector plugin type not found");

    List<IPlugin> connectorPlugins = registry.getPlugins(HConnectorPluginType.class);
    assertTrue(!connectorPlugins.isEmpty(), "Plugins list empty");
  }
}
