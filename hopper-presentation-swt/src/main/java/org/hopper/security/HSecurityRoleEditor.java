package org.hopper.security;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper security roles")
public class HSecurityRoleEditor extends HAnnotatedMetadataEditor<HSecurityRole> {
  public HSecurityRoleEditor(
      HopGui hopGui, MetadataManager<HSecurityRole> manager, HSecurityRole metadata) {
    super(hopGui, manager, metadata);
  }
}
