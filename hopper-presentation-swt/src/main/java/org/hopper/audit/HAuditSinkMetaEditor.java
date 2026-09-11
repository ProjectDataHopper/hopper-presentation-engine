package org.hopper.audit;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper audit sinks")
public class HAuditSinkMetaEditor extends HAnnotatedMetadataEditor<HAuditSinkMeta> {
  public HAuditSinkMetaEditor(
      HopGui hopGui, MetadataManager<HAuditSinkMeta> manager, HAuditSinkMeta metadata) {
    super(hopGui, manager, metadata);
  }
}
