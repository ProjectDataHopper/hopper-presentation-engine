package org.hopper.core.plugin;

import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.hopper.core.dialog.IHDialog;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;

import java.lang.reflect.Constructor;

public class HPluginUtil {

  public static IHDialog loadComponentDialogClass(
      IPlugin plugin,
      HPresentation presentation,
      HComponent component,
      String dialogClassName)
      throws Exception {

    PluginRegistry registry = PluginRegistry.getInstance();
    ClassLoader classLoader = registry.getClassLoader(plugin);

    // IHComponent hopperComponent = (IHComponent) registry.loadClass( plugin );

    Class<?>[] paramClasses = new Class<?>[] {HPresentation.class, HComponent.class};
    Object[] paramArgs = new Object[] {presentation, component};

    Class<IHDialog> dialogClass = registry.getClass(plugin, dialogClassName);
    Constructor<IHDialog> dialogConstructor = dialogClass.getConstructor(paramClasses);

    return dialogConstructor.newInstance(paramArgs);
  }
}
