package org.hopper.core;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper database connections")
public class HDatabaseConnectionEditor extends HAnnotatedMetadataEditor<HDatabaseConnection> {
  public HDatabaseConnectionEditor(
      HopGui hopGui, MetadataManager<HDatabaseConnection> manager, HDatabaseConnection metadata) {
    super(hopGui, manager, metadata);
  }
}
