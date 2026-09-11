package org.hopper.core.history;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper user history")
public class HUserHistoryEditor extends HAnnotatedMetadataEditor<HUserHistory> {
  public HUserHistoryEditor(
      HopGui hopGui, MetadataManager<HUserHistory> manager, HUserHistory metadata) {
    super(hopGui, manager, metadata);
  }
}
