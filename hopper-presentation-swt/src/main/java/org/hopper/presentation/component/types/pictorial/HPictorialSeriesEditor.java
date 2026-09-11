package org.hopper.presentation.component.types.pictorial;

import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for pictorial series")
public class HPictorialSeriesEditor extends HAnnotatedMetadataEditor<HPictorialSeries> {
  public HPictorialSeriesEditor(
      HopGui hopGui, MetadataManager<HPictorialSeries> manager, HPictorialSeries metadata) {
    super(hopGui, manager, metadata);
  }
}
