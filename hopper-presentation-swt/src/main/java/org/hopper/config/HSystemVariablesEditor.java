package org.hopper.config;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper system variables")
public class HSystemVariablesEditor extends HAnnotatedMetadataEditor<HSystemVariables> {
  public HSystemVariablesEditor(
      HopGui hopGui, MetadataManager<HSystemVariables> manager, HSystemVariables metadata) {
    super(hopGui, manager, metadata);
  }
}
