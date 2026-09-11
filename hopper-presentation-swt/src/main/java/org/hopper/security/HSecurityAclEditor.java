package org.hopper.security;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper security ACLs")
public class HSecurityAclEditor extends HAnnotatedMetadataEditor<HSecurityAcl> {
  public HSecurityAclEditor(
      HopGui hopGui, MetadataManager<HSecurityAcl> manager, HSecurityAcl metadata) {
    super(hopGui, manager, metadata);
  }
}
