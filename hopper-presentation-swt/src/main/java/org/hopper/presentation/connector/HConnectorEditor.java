package org.hopper.presentation.connector;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper connectors")
public class HConnectorEditor extends HAnnotatedMetadataEditor<HConnector> {
  public HConnectorEditor(
      HopGui hopGui, MetadataManager<HConnector> manager, HConnector metadata) {
    super(hopGui, manager, metadata);
  }
}
