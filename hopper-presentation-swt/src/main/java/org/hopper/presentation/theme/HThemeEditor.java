package org.hopper.presentation.theme;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper themes")
public class HThemeEditor extends HAnnotatedMetadataEditor<HTheme> {
  public HThemeEditor(HopGui hopGui, MetadataManager<HTheme> manager, HTheme metadata) {
    super(hopGui, manager, metadata);
  }
}
