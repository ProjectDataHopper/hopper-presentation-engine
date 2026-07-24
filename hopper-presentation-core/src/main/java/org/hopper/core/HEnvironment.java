package org.hopper.core;

import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.hopper.audit.plugin.HAuditPluginType;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.plugin.HGuiRegistry;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.HConnectorPluginType;

/**
 * Initializes Hop and registers Hopper component/connector/audit (and metadata) plugin types.
 *
 * <p>Safe to call repeatedly and from multiple threads; initialization runs once.
 */
public class HEnvironment {

  /** Has the Hopper environment been initialized? */
  private static volatile boolean initialized;

  private HEnvironment() {
    // utility
  }

  public static synchronized void init() throws HException {
    if (initialized) {
      return;
    }

    try {
      if (!HopClientEnvironment.isInitialized()) {
        HopClientEnvironment.init();
      }
    } catch (HopException e) {
      throw new HException("Unable to initialize the Hop client API environment", e);
    }

    try {
      // MetadataPluginType is not part of the default HopClientEnvironment plugin set.
      PluginRegistry.addPluginType(MetadataPluginType.getInstance());
      PluginRegistry.addPluginType(HComponentPluginType.getInstance());
      PluginRegistry.addPluginType(HConnectorPluginType.getInstance());
      PluginRegistry.addPluginType(HAuditPluginType.getInstance());
      // registerType() skips types already loaded; only new Hopper/metadata types are scanned.
      PluginRegistry.init();
      HGuiRegistry.getInstance().scanFromPluginRegistry();
    } catch (Exception e) {
      throw new HException("Unable to register hopper plugin types", e);
    }

    initialized = true;
  }

  public static boolean isInitialized() {
    return initialized;
  }
}
