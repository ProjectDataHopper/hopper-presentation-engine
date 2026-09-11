package org.hopper.presentation.swt.meta;

import org.apache.hop.core.Const;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.metadata.MetadataEditor;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * Metadata editor that builds its form from {@code @HopMetadataProperty} / {@code @HWidgetElement}
 * annotations on the managed type.
 *
 * <p>Hop's {@code MetadataManager} loads {@code <TypeName>Editor} from the same package as the
 * metadata class. Thin subclasses in those packages extend this editor.
 */
public class HAnnotatedMetadataEditor<T extends IHopMetadata> extends MetadataEditor<T> {

  private Text wName;
  private HMetadataForm form;

  public HAnnotatedMetadataEditor(HopGui hopGui, MetadataManager<T> manager, T metadata) {
    super(hopGui, manager, metadata);
  }

  @Override
  public void createControl(Composite parent) {
    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    Label wlName = new Label(parent, SWT.RIGHT);
    PropsUi.setLook(wlName);
    wlName.setText("Name");
    FormData fdlName = new FormData();
    fdlName.top = new FormAttachment(0, margin);
    fdlName.left = new FormAttachment(0, 0);
    fdlName.right = new FormAttachment(middle, -margin);
    wlName.setLayoutData(fdlName);

    wName = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wName);
    FormData fdName = new FormData();
    fdName.top = new FormAttachment(wlName, 0, SWT.CENTER);
    fdName.left = new FormAttachment(middle, 0);
    fdName.right = new FormAttachment(100, 0);
    wName.setLayoutData(fdName);

    Composite formParent = new Composite(parent, SWT.NONE);
    formParent.setLayout(new FormLayout());
    FormData fdForm = new FormData();
    fdForm.left = new FormAttachment(0, 0);
    fdForm.right = new FormAttachment(100, 0);
    fdForm.top = new FormAttachment(wName, margin);
    fdForm.bottom = new FormAttachment(100, 0);
    formParent.setLayoutData(fdForm);
    PropsUi.setLook(formParent);

    form =
        new HMetadataForm(
            formParent,
            getMetadata(),
            manager.getVariables(),
            hopGui.getMetadataProvider(),
            this::setChanged);

    setWidgetsContent();
    wName.addModifyListener(e -> setChanged());
  }

  @Override
  public void setWidgetsContent() {
    T meta = getMetadata();
    if (wName != null) {
      wName.setText(Const.NVL(meta.getName(), ""));
    }
    if (form != null) {
      form.setWidgetsContent();
    }
  }

  @Override
  public void getWidgetsContent(T meta) {
    meta.setName(wName.getText());
    if (form != null) {
      form.getWidgetsContent();
    }
  }
}
