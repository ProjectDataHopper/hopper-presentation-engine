package org.hopper.config;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper server settings")
public class HServerSettingsEditor extends HAnnotatedMetadataEditor<HServerSettings> {
  public HServerSettingsEditor(
      HopGui hopGui, MetadataManager<HServerSettings> manager, HServerSettings metadata) {
    super(hopGui, manager, metadata);
  }
}
