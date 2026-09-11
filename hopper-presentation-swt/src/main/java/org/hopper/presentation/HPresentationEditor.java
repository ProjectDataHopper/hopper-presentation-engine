package org.hopper.presentation;

import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;
import org.hopper.presentation.swt.meta.HAnnotatedMetadataEditor;

@GuiPlugin(description = "Editor for Hopper presentations")
public class HPresentationEditor extends HAnnotatedMetadataEditor<HPresentation> {

  public static final Class<?> PKG = HPresentationEditor.class;

  public HPresentationEditor(
      HopGui hopGui, MetadataManager<HPresentation> manager, HPresentation metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public Button[] createButtonsForButtonBar(Composite parent) {
    Button preview = new Button(parent, SWT.PUSH);
    preview.setText(BaseMessages.getString(PKG, "HPresentationEditor.Preview"));
    preview.addListener(SWT.Selection, e -> openPreview());
    return new Button[] {preview};
  }

  private void openPreview() {
    try {
      getWidgetsContent(getMetadata());
      Shell shell = new Shell(getShell(), SWT.SHELL_TRIM);
      shell.setText(
          Const.NVL(getMetadata().getName(), BaseMessages.getString(PKG, "HPresentationEditor.Untitled")));
      shell.setLayout(new FormLayout());
      HPresentationViewer viewer =
          new HPresentationViewer(
              shell,
              new LoggingObject("presentation-preview"),
              hopGui.getMetadataProvider(),
              getMetadata(),
              HPresentationChrome.FULL,
              null);
      FormData fd = new FormData();
      fd.left = new FormAttachment(0, 0);
      fd.top = new FormAttachment(0, 0);
      fd.right = new FormAttachment(100, 0);
      fd.bottom = new FormAttachment(100, 0);
      viewer.setLayoutData(fd);
      shell.setSize(1000, 800);
      shell.open();
    } catch (Exception e) {
      new ErrorDialog(
          getShell(),
          BaseMessages.getString(PKG, "HPresentationEditor.Preview.Error.Title"),
          BaseMessages.getString(PKG, "HPresentationEditor.Preview.Error.Message"),
          e);
    }
  }
}
