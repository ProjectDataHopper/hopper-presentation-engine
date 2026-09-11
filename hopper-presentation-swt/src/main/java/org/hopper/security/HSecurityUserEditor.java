package org.hopper.security;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper security users")
public class HSecurityUserEditor extends HAnnotatedMetadataEditor<HSecurityUser> {
  public HSecurityUserEditor(
      HopGui hopGui, MetadataManager<HSecurityUser> manager, HSecurityUser metadata) {
    super(hopGui, manager, metadata);
  }
}
