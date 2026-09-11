package org.hopper.presentation.swt.meta;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabasePluginType;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FontDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.theme.HTheme;

/**
 * Builds an SWT form from {@link org.apache.hop.metadata.api.HopMetadataProperty} and {@link
 * org.hopper.core.gui.plugin.HWidgetElement} annotations.
 */
public class HMetadataForm {

  public static final Class<?> PKG = HMetadataForm.class;

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private final Object bean;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final Runnable onModify;
  private final List<Binding> bindings = new ArrayList<>();
  private final Composite body;
  private final ScrolledComposite scrolled;

  public HMetadataForm(
      Composite parent,
      Object bean,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onModify) {
    this(parent, bean, variables, metadataProvider, onModify, true);
  }

  public HMetadataForm(
      Composite parent,
      Object bean,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onModify,
      boolean scroll) {
    this.bean = bean;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.onModify = onModify != null ? onModify : () -> {};

    if (scroll) {
      scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
      scrolled.setLayout(new FormLayout());
      FormData fdScrolled = fill(null);
      scrolled.setLayoutData(fdScrolled);
      PropsUi.setLook(scrolled);
      body = new Composite(scrolled, SWT.NONE);
      body.setLayout(new FormLayout());
      PropsUi.setLook(body);
      scrolled.setContent(body);
      scrolled.setExpandHorizontal(true);
      scrolled.setExpandVertical(true);
    } else {
      scrolled = null;
      body = new Composite(parent, SWT.NONE);
      body.setLayout(new FormLayout());
      body.setLayoutData(fill(null));
      PropsUi.setLook(body);
    }

    Control last = null;
    for (HMetadataFields.Spec spec : HMetadataFields.discover(bean.getClass())) {
      last = addField(spec, last);
    }
    relayout();
  }

  public Composite getBody() {
    return body;
  }

  public void setWidgetsContent() {
    for (Binding binding : bindings) {
      binding.load();
    }
  }

  public void getWidgetsContent() {
    for (Binding binding : bindings) {
      binding.save();
    }
  }

  private void relayout() {
    body.layout(true, true);
    if (scrolled != null) {
      scrolled.setMinSize(body.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }
  }

  private Control addField(HMetadataFields.Spec spec, Control last) {
    Class<?> type = spec.type();
    if (HMetadataFields.isPluginInterface(type)) {
      return addPluginField(spec, last);
    }
    if (type == HColorRGB.class) {
      return addColorField(spec, last);
    }
    if (type == HFont.class) {
      return addFontField(spec, last);
    }
    if (spec.isMap()) {
      return addMapField(spec, last);
    }
    if (spec.isList()) {
      return addListField(spec, last);
    }
    if (!HMetadataFields.isSimple(type)
        && spec.widgetType() != HWidgetType.TEXT
        && spec.widgetType() != HWidgetType.MULTI_LINE_TEXT
        && spec.widgetType() != HWidgetType.COMBO
        && spec.widgetType() != HWidgetType.CHECKBOX
        && spec.widgetType() != HWidgetType.FILENAME
        && spec.widgetType() != HWidgetType.FOLDER
        && spec.widgetType() != HWidgetType.METADATA) {
      return addNestedObjectField(spec, last);
    }
    return addSimpleField(spec, last);
  }

  private Control addSimpleField(HMetadataFields.Spec spec, Control last) {
    int middle = PropsUi.getInstance().getMiddlePct();
    Label fieldLabel = label(spec, last);
    Control widget;
    Binding binding;
    if (spec.widgetType() == HWidgetType.CHECKBOX
        || spec.type() == boolean.class
        || spec.type() == Boolean.class) {
      Button check = new Button(body, SWT.CHECK);
      PropsUi.setLook(check);
      check.addListener(SWT.Selection, e -> onModify.run());
      widget = check;
      binding =
          new Binding() {
            @Override
            public void load() {
              Object value = HMetadataFields.get(bean, spec.field());
              check.setSelection(Boolean.TRUE.equals(value));
            }

            @Override
            public void save() {
              HMetadataFields.set(bean, spec.field(), check.getSelection());
            }
          };
    } else if (spec.widgetType() == HWidgetType.MULTI_LINE_TEXT) {
      Text text = new Text(body, SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
      PropsUi.setLook(text);
      text.addModifyListener(e -> onModify.run());
      widget = text;
      FormData fdText = new FormData();
      fdText.left = new FormAttachment(middle, 0);
      fdText.right = new FormAttachment(100, 0);
      fdText.top = new FormAttachment(fieldLabel, 0, SWT.TOP);
      fdText.height = Math.max(48, spec.multiLineTextHeight() * 18);
      text.setLayoutData(fdText);
      bindings.add(plainTextBinding(spec, text));
      return text;
    } else if (spec.widgetType() == HWidgetType.FILENAME
        || spec.widgetType() == HWidgetType.FOLDER) {
      return addPathField(spec, fieldLabel, spec.widgetType() == HWidgetType.FOLDER);
    } else if (spec.widgetType() == HWidgetType.COMBO
        || spec.type().isEnum()
        || spec.comboSource() != HComboSource.NONE
        || spec.widgetType() == HWidgetType.METADATA) {
      boolean readOnly = spec.type().isEnum();
      Combo combo = new Combo(body, SWT.BORDER | (readOnly ? SWT.READ_ONLY : SWT.NONE));
      PropsUi.setLook(combo);
      combo.setItems(comboOptions(spec));
      combo.addListener(SWT.Selection, e -> onModify.run());
      combo.addListener(SWT.Modify, e -> onModify.run());
      widget = combo;
      binding =
          new Binding() {
            @Override
            public void load() {
              Object value = HMetadataFields.get(bean, spec.field());
              combo.setText(
                  value == null
                      ? ""
                      : String.valueOf(value instanceof Enum<?> enumerated ? enumerated.name() : value));
            }

            @Override
            public void save() {
              HMetadataFields.set(bean, spec.field(), combo.getText());
            }
          };
    } else if (spec.password()) {
      TextVar text = new TextVar(variables, body, SWT.SINGLE | SWT.LEFT | SWT.BORDER | SWT.PASSWORD);
      text.addModifyListener(e -> onModify.run());
      widget = text;
      binding = textVarBinding(spec, text);
    } else if (spec.type() == Date.class) {
      Text text = new Text(body, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
      PropsUi.setLook(text);
      text.addModifyListener(e -> onModify.run());
      widget = text;
      binding = dateBinding(spec, text);
    } else {
      TextVar text = new TextVar(variables, body, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
      text.addModifyListener(e -> onModify.run());
      widget = text;
      binding = textVarBinding(spec, text);
    }
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(fieldLabel, 0, SWT.CENTER);
    widget.setLayoutData(fd);
    bindings.add(binding);
    return widget;
  }

  private Control addPathField(HMetadataFields.Spec spec, Label fieldLabel, boolean folder) {
    int middle = PropsUi.getInstance().getMiddlePct();
    Composite row = new Composite(body, SWT.NONE);
    row.setLayout(new FormLayout());
    PropsUi.setLook(row);
    Button browse = new Button(row, SWT.PUSH);
    browse.setText(BaseMessages.getString(PKG, "HMetadataForm.Browse"));
    FormData fdBrowse = new FormData();
    fdBrowse.right = new FormAttachment(100, 0);
    fdBrowse.top = new FormAttachment(0, 0);
    browse.setLayoutData(fdBrowse);
    TextVar text = new TextVar(variables, row, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    FormData fdText = new FormData();
    fdText.left = new FormAttachment(0, 0);
    fdText.right = new FormAttachment(browse, -PropsUi.getMargin());
    fdText.top = new FormAttachment(browse, 0, SWT.CENTER);
    text.setLayoutData(fdText);
    text.addModifyListener(e -> onModify.run());
    browse.addListener(
        SWT.Selection,
        e -> {
          String chosen =
              folder
                  ? BaseDialog.presentDirectoryDialog(body.getShell(), text, variables)
                  : BaseDialog.presentFileDialog(
                      body.getShell(),
                      new String[] {"*.*"},
                      new String[] {"All files"},
                      true);
          if (chosen != null) {
            text.setText(chosen);
            onModify.run();
          }
        });
    FormData fdRow = new FormData();
    fdRow.left = new FormAttachment(middle, 0);
    fdRow.right = new FormAttachment(100, 0);
    fdRow.top = new FormAttachment(fieldLabel, 0, SWT.CENTER);
    row.setLayoutData(fdRow);
    bindings.add(textVarBinding(spec, text));
    return row;
  }

  private Binding textVarBinding(HMetadataFields.Spec spec, TextVar text) {
    return new Binding() {
      @Override
      public void load() {
        Object value = HMetadataFields.get(bean, spec.field());
        text.setText(value == null ? "" : String.valueOf(value));
      }

      @Override
      public void save() {
        HMetadataFields.set(bean, spec.field(), text.getText());
      }
    };
  }

  private Binding plainTextBinding(HMetadataFields.Spec spec, Text text) {
    return new Binding() {
      @Override
      public void load() {
        Object value = HMetadataFields.get(bean, spec.field());
        text.setText(value == null ? "" : String.valueOf(value));
      }

      @Override
      public void save() {
        HMetadataFields.set(bean, spec.field(), text.getText());
      }
    };
  }

  private Binding dateBinding(HMetadataFields.Spec spec, Text text) {
    return new Binding() {
      @Override
      public void load() {
        Object value = HMetadataFields.get(bean, spec.field());
        if (value instanceof Date date) {
          text.setText(DATE_FORMAT.format(date));
        } else {
          text.setText(value == null ? "" : String.valueOf(value));
        }
      }

      @Override
      public void save() {
        String raw = text.getText();
        if (StringUtils.isBlank(raw)) {
          HMetadataFields.set(bean, spec.field(), null);
          return;
        }
        try {
          HMetadataFields.set(bean, spec.field(), DATE_FORMAT.parse(raw.trim()));
        } catch (Exception e) {
          HMetadataFields.set(bean, spec.field(), new Date());
        }
      }
    };
  }

  private Control addColorField(HMetadataFields.Spec spec, Control last) {
    int middle = PropsUi.getInstance().getMiddlePct();
    Label fieldLabel = label(spec, last);
    Composite row = new Composite(body, SWT.NONE);
    row.setLayout(new FormLayout());
    PropsUi.setLook(row);
    Text text = new Text(row, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    Button pick = new Button(row, SWT.PUSH);
    pick.setText("…");
    FormData fdPick = new FormData();
    fdPick.right = new FormAttachment(100, 0);
    fdPick.top = new FormAttachment(0, 0);
    pick.setLayoutData(fdPick);
    FormData fdText = new FormData();
    fdText.left = new FormAttachment(0, 0);
    fdText.right = new FormAttachment(pick, -PropsUi.getMargin());
    fdText.top = new FormAttachment(pick, 0, SWT.CENTER);
    text.setLayoutData(fdText);
    text.addModifyListener(e -> onModify.run());
    pick.addListener(
        SWT.Selection,
        e -> {
          HColorRGB chosen = pickColor(text.getText());
          if (chosen != null) {
            text.setText(chosen.getHexColor());
            onModify.run();
          }
        });
    FormData fdRow = new FormData();
    fdRow.left = new FormAttachment(middle, 0);
    fdRow.right = new FormAttachment(100, 0);
    fdRow.top = new FormAttachment(fieldLabel, 0, SWT.CENTER);
    row.setLayoutData(fdRow);
    bindings.add(
        new Binding() {
          @Override
          public void load() {
            Object value = HMetadataFields.get(bean, spec.field());
            if (value instanceof HColorRGB color) {
              text.setText(color.getHexColor());
            } else {
              text.setText(value == null ? "" : String.valueOf(value));
            }
          }

          @Override
          public void save() {
            HMetadataFields.set(bean, spec.field(), parseColor(text.getText()));
          }
        });
    return row;
  }

  private Control addFontField(HMetadataFields.Spec spec, Control last) {
    int middle = PropsUi.getInstance().getMiddlePct();
    Label fieldLabel = label(spec, last);
    Composite row = new Composite(body, SWT.NONE);
    row.setLayout(new FormLayout());
    PropsUi.setLook(row);
    Text text = new Text(row, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(text);
    Button pick = new Button(row, SWT.PUSH);
    pick.setText(BaseMessages.getString(PKG, "HMetadataForm.Font"));
    FormData fdPick = new FormData();
    fdPick.right = new FormAttachment(100, 0);
    fdPick.top = new FormAttachment(0, 0);
    pick.setLayoutData(fdPick);
    FormData fdText = new FormData();
    fdText.left = new FormAttachment(0, 0);
    fdText.right = new FormAttachment(pick, -PropsUi.getMargin());
    fdText.top = new FormAttachment(pick, 0, SWT.CENTER);
    text.setLayoutData(fdText);
    text.addModifyListener(e -> onModify.run());
    pick.addListener(
        SWT.Selection,
        e -> {
          HFont chosen = pickFont(parseFont(text.getText()));
          if (chosen != null) {
            text.setText(formatFont(chosen));
            onModify.run();
          }
        });
    FormData fdRow = new FormData();
    fdRow.left = new FormAttachment(middle, 0);
    fdRow.right = new FormAttachment(100, 0);
    fdRow.top = new FormAttachment(fieldLabel, 0, SWT.CENTER);
    row.setLayoutData(fdRow);
    bindings.add(
        new Binding() {
          @Override
          public void load() {
            Object value = HMetadataFields.get(bean, spec.field());
            if (value instanceof HFont font) {
              text.setText(formatFont(font));
            } else {
              text.setText(value == null ? "" : String.valueOf(value));
            }
          }

          @Override
          public void save() {
            HMetadataFields.set(bean, spec.field(), parseFont(text.getText()));
          }
        });
    return row;
  }

  private Control addPluginField(HMetadataFields.Spec spec, Control last) {
    Label fieldLabel = label(spec, last);
    Combo combo = new Combo(body, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(combo);
    Map<String, String> idByLabel = pluginOptions(spec.type());
    combo.setItems(idByLabel.keySet().toArray(String[]::new));
    FormData fdCombo = new FormData();
    fdCombo.left = new FormAttachment(PropsUi.getInstance().getMiddlePct(), 0);
    fdCombo.right = new FormAttachment(100, 0);
    fdCombo.top = new FormAttachment(fieldLabel, 0, SWT.CENTER);
    combo.setLayoutData(fdCombo);

    Composite nested = new Composite(body, SWT.NONE);
    nested.setLayout(new FormLayout());
    PropsUi.setLook(nested);
    FormData fdNested = new FormData();
    fdNested.left = new FormAttachment(0, 0);
    fdNested.right = new FormAttachment(100, 0);
    fdNested.top = new FormAttachment(combo, PropsUi.getMargin());
    nested.setLayoutData(fdNested);

    PluginBinding pluginBinding = new PluginBinding(spec, combo, nested, idByLabel);
    bindings.add(pluginBinding);
    combo.addListener(SWT.Selection, e -> pluginBinding.changeType());
    return nested;
  }

  private Control addListField(HMetadataFields.Spec spec, Control last) {
    return addCollectionField(spec, last, false);
  }

  private Control addMapField(HMetadataFields.Spec spec, Control last) {
    return addCollectionField(spec, last, true);
  }

  private Control addCollectionField(HMetadataFields.Spec spec, Control last, boolean map) {
    int margin = PropsUi.getMargin();
    Label fieldLabel = new Label(body, SWT.LEFT);
    PropsUi.setLook(fieldLabel);
    fieldLabel.setText(spec.label());
    if (StringUtils.isNotBlank(spec.toolTip())) {
      fieldLabel.setToolTipText(spec.toolTip());
    }
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(100, 0);
    fdl.top = last != null ? new FormAttachment(last, margin * 2) : new FormAttachment(0, margin);
    fieldLabel.setLayoutData(fdl);

    org.eclipse.swt.widgets.List list =
        new org.eclipse.swt.widgets.List(body, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
    PropsUi.setLook(list);
    FormData fdList = new FormData();
    fdList.left = new FormAttachment(0, 0);
    fdList.right = new FormAttachment(100, -90);
    fdList.top = new FormAttachment(fieldLabel, margin);
    fdList.height = 90;
    list.setLayoutData(fdList);

    Button add = new Button(body, SWT.PUSH);
    add.setText(BaseMessages.getString(PKG, "HMetadataForm.Add"));
    FormData fdAdd = new FormData();
    fdAdd.left = new FormAttachment(list, margin);
    fdAdd.right = new FormAttachment(100, 0);
    fdAdd.top = new FormAttachment(list, 0, SWT.TOP);
    add.setLayoutData(fdAdd);
    Button edit = new Button(body, SWT.PUSH);
    edit.setText(BaseMessages.getString(PKG, "HMetadataForm.Edit"));
    FormData fdEdit = new FormData();
    fdEdit.left = new FormAttachment(list, margin);
    fdEdit.right = new FormAttachment(100, 0);
    fdEdit.top = new FormAttachment(add, margin);
    edit.setLayoutData(fdEdit);
    Button remove = new Button(body, SWT.PUSH);
    remove.setText(BaseMessages.getString(PKG, "HMetadataForm.Remove"));
    FormData fdRemove = new FormData();
    fdRemove.left = new FormAttachment(list, margin);
    fdRemove.right = new FormAttachment(100, 0);
    fdRemove.top = new FormAttachment(edit, margin);
    remove.setLayoutData(fdRemove);

    CollectionBinding binding = map ? new MapBinding(spec, list) : new ListBinding(spec, list);
    bindings.add(binding);
    add.addListener(SWT.Selection, e -> binding.add());
    edit.addListener(SWT.Selection, e -> binding.edit());
    remove.addListener(SWT.Selection, e -> binding.remove());
    list.addListener(SWT.DefaultSelection, e -> binding.edit());
    return list;
  }

  private Control addNestedObjectField(HMetadataFields.Spec spec, Control last) {
    int margin = PropsUi.getMargin();
    Label fieldLabel = new Label(body, SWT.LEFT);
    PropsUi.setLook(fieldLabel);
    fieldLabel.setText(spec.label());
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(100, 0);
    fdl.top = last != null ? new FormAttachment(last, margin * 2) : new FormAttachment(0, margin);
    fieldLabel.setLayoutData(fdl);
    Button edit = new Button(body, SWT.PUSH);
    edit.setText(BaseMessages.getString(PKG, "HMetadataForm.EditEllipsis"));
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.top = new FormAttachment(fieldLabel, margin);
    edit.setLayoutData(fd);
    edit.addListener(
        SWT.Selection,
        e -> {
          Object value = HMetadataFields.get(bean, spec.field());
          if (value == null) {
            value = HMetadataFields.newInstance(spec.type());
            HMetadataFields.set(bean, spec.field(), value);
          }
          if (editBean(body.getShell(), spec.label(), value)) {
            onModify.run();
          }
        });
    return edit;
  }

  private Label label(HMetadataFields.Spec spec, Control last) {
    int middle = PropsUi.getInstance().getMiddlePct();
    int margin = PropsUi.getMargin();
    Label fieldLabel = new Label(body, SWT.RIGHT);
    PropsUi.setLook(fieldLabel);
    fieldLabel.setText(spec.label());
    if (StringUtils.isNotBlank(spec.toolTip())) {
      fieldLabel.setToolTipText(spec.toolTip());
    }
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(middle, -margin);
    fd.top = last != null ? new FormAttachment(last, margin) : new FormAttachment(0, margin);
    fieldLabel.setLayoutData(fd);
    return fieldLabel;
  }

  private String[] comboOptions(HMetadataFields.Spec spec) {
    if (spec.type().isEnum()) {
      Object[] constants = spec.type().getEnumConstants();
      String[] names = new String[constants.length];
      for (int i = 0; i < constants.length; i++) {
        names[i] = ((Enum<?>) constants[i]).name();
      }
      return names;
    }
    if (StringUtils.isNotBlank(spec.comboValuesMethod())) {
      String[] fromMethod = invokeComboValues(spec);
      if (fromMethod.length > 0) {
        return fromMethod;
      }
    }
    if (spec.comboSource() == HComboSource.CONNECTORS) {
      return metadataNames(HConnector.class);
    }
    if (spec.comboSource() == HComboSource.THEMES) {
      return metadataNames(HTheme.class);
    }
    if (spec.comboSource() == HComboSource.METADATA || spec.widgetType() == HWidgetType.METADATA) {
      return metadataNames(resolveMetadataClass(spec.metadataKey()));
    }
    if ("layoutMode".equals(spec.field().getName())) {
      return new String[] {"paginated", "continuous"};
    }
    if ("databaseTypeCode".equals(spec.field().getName())) {
      return databaseTypeCodes();
    }
    return new String[0];
  }

  private String[] invokeComboValues(HMetadataFields.Spec spec) {
    try {
      Class<?> owner = spec.field().getDeclaringClass();
      Object target = owner.isInstance(bean) ? bean : owner.getDeclaredConstructor().newInstance();
      Method method = owner.getMethod(spec.comboValuesMethod());
      Object result = method.invoke(target);
      if (result instanceof String[] array) {
        return array;
      }
      if (result instanceof List<?> list) {
        return list.stream().map(String::valueOf).toArray(String[]::new);
      }
    } catch (Exception ignored) {
      // Leave empty; the combo stays editable.
    }
    return new String[0];
  }

  private String[] databaseTypeCodes() {
    try {
      List<IPlugin> plugins = PluginRegistry.getInstance().getPlugins(DatabasePluginType.class);
      if (plugins == null || plugins.isEmpty()) {
        return new String[] {"POSTGRESQL", "MYSQL", "MSSQLNATIVE", "ORACLE", "SNOWFLAKE"};
      }
      List<String> ids = new ArrayList<>();
      for (IPlugin plugin : plugins) {
        if (plugin.getIds() != null && plugin.getIds().length > 0) {
          ids.add(plugin.getIds()[0]);
        }
      }
      ids.sort(String.CASE_INSENSITIVE_ORDER);
      return ids.toArray(String[]::new);
    } catch (Exception e) {
      return new String[] {"POSTGRESQL", "MYSQL", "MSSQLNATIVE", "ORACLE", "SNOWFLAKE"};
    }
  }

  private String[] metadataNames(Class<? extends IHopMetadata> type) {
    if (type == null || metadataProvider == null) {
      return new String[0];
    }
    try {
      List<String> names = metadataProvider.getSerializer(type).listObjectNames();
      names.sort(String.CASE_INSENSITIVE_ORDER);
      return names.toArray(String[]::new);
    } catch (Exception e) {
      return new String[0];
    }
  }

  @SuppressWarnings("unchecked")
  private Class<? extends IHopMetadata> resolveMetadataClass(String key) {
    if (StringUtils.isBlank(key) || "theme".equals(key)) {
      return HTheme.class;
    }
    if ("connector".equals(key)) {
      return HConnector.class;
    }
    if ("presentation".equals(key)) {
      return HPresentation.class;
    }
    if ("hopper-database-connection".equals(key)) {
      return org.hopper.core.HDatabaseConnection.class;
    }
    try {
      IPlugin plugin = PluginRegistry.getInstance().getPlugin(MetadataPluginType.class, key);
      if (plugin != null) {
        String className = plugin.getClassMap().get(IHopMetadata.class);
        if (className != null) {
          return (Class<? extends IHopMetadata>)
              PluginRegistry.getInstance().getClassLoader(plugin).loadClass(className);
        }
      }
    } catch (Exception ignored) {
      // Fall through.
    }
    return null;
  }

  private Map<String, String> pluginOptions(Class<?> type) {
    Map<String, String> map = new LinkedHashMap<>();
    List<IPlugin> plugins =
        new ArrayList<>(
            type == IHConnector.class
                ? PluginRegistry.getInstance().getPlugins(HConnectorPluginType.class)
                : PluginRegistry.getInstance().getPlugins(HComponentPluginType.class));
    plugins.sort(
        Comparator.comparing(IPlugin::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
    for (IPlugin plugin : plugins) {
      String id = plugin.getIds()[0];
      map.put(plugin.getName() + " (" + id + ")", id);
    }
    return map;
  }

  private HColorRGB pickColor(String current) {
    org.eclipse.swt.widgets.ColorDialog dialog =
        new org.eclipse.swt.widgets.ColorDialog(body.getShell());
    HColorRGB parsed = parseColor(current);
    dialog.setRGB(new RGB(parsed.getR(), parsed.getG(), parsed.getB()));
    RGB rgb = dialog.open();
    if (rgb == null) {
      return null;
    }
    return new HColorRGB(rgb.red, rgb.green, rgb.blue);
  }

  private HFont pickFont(HFont current) {
    FontDialog dialog = new FontDialog(body.getShell());
    dialog.setFontList(new org.eclipse.swt.graphics.FontData[] {toFontData(current)});
    org.eclipse.swt.graphics.FontData chosen = dialog.open();
    if (chosen == null) {
      return null;
    }
    boolean bold = (chosen.getStyle() & SWT.BOLD) != 0;
    boolean italic = (chosen.getStyle() & SWT.ITALIC) != 0;
    return new HFont(chosen.getName(), String.valueOf(chosen.getHeight()), bold, italic);
  }

  private static HColorRGB parseColor(String text) {
    if (StringUtils.isBlank(text)) {
      return new HColorRGB();
    }
    try {
      return new HColorRGB(text.trim());
    } catch (Exception e) {
      return new HColorRGB();
    }
  }

  private static String formatFont(HFont font) {
    return Const.NVL(font.getFontName(), "")
        + ","
        + Const.NVL(font.getFontSize(), "12")
        + ","
        + font.isBold()
        + ","
        + font.isItalic();
  }

  private static HFont parseFont(String text) {
    String[] parts = Const.NVL(text, "").split(",");
    String name = parts.length > 0 ? parts[0] : "Arial";
    String size = parts.length > 1 ? parts[1] : "12";
    boolean bold = parts.length > 2 && Boolean.parseBoolean(parts[2]);
    boolean italic = parts.length > 3 && Boolean.parseBoolean(parts[3]);
    return new HFont(name, size, bold, italic);
  }

  private static org.eclipse.swt.graphics.FontData toFontData(HFont font) {
    int style = SWT.NORMAL;
    if (font != null && font.isBold()) {
      style |= SWT.BOLD;
    }
    if (font != null && font.isItalic()) {
      style |= SWT.ITALIC;
    }
    int size = 12;
    try {
      size = Integer.parseInt(Const.NVL(font == null ? "12" : font.getFontSize(), "12"));
    } catch (NumberFormatException ignored) {
      // keep default
    }
    String name = font == null ? "Arial" : Const.NVL(font.getFontName(), "Arial");
    return new org.eclipse.swt.graphics.FontData(name, size, style);
  }

  boolean editBean(Shell parent, String title, Object value) {
    return openBeanDialog(parent, title, value, variables, metadataProvider);
  }

  public static boolean openBeanDialog(
      Shell parent,
      String title,
      Object value,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    shell.setText(title);
    shell.setLayout(new FormLayout());
    Composite composite = new Composite(shell, SWT.NONE);
    composite.setLayout(new FormLayout());
    FormData fdComp = new FormData();
    fdComp.left = new FormAttachment(0, 0);
    fdComp.right = new FormAttachment(100, 0);
    fdComp.top = new FormAttachment(0, 0);
    fdComp.bottom = new FormAttachment(100, -50);
    composite.setLayoutData(fdComp);
    HMetadataForm form = new HMetadataForm(composite, value, variables, metadataProvider, () -> {});
    form.setWidgetsContent();
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText(BaseMessages.getString(PKG, "HMetadataForm.Ok"));
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText(BaseMessages.getString(PKG, "HMetadataForm.Cancel"));
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {ok, cancel}, PropsUi.getMargin(), composite);
    final boolean[] saved = {false};
    ok.addListener(
        SWT.Selection,
        e -> {
          form.getWidgetsContent();
          saved[0] = true;
          shell.dispose();
        });
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    BaseDialog.defaultShellHandling(
        shell, c -> ok.notifyListeners(SWT.Selection, null), c -> shell.dispose());
    return saved[0];
  }

  private static FormData fill(Control top) {
    FormData fd = new FormData();
    fd.left = new FormAttachment(0, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = top == null ? new FormAttachment(0, 0) : new FormAttachment(top, 0);
    fd.bottom = new FormAttachment(100, 0);
    return fd;
  }

  private interface Binding {
    void load();

    void save();
  }

  private abstract class CollectionBinding implements Binding {
    abstract void add();

    abstract void edit();

    abstract void remove();
  }

  private final class PluginBinding implements Binding {
    private final HMetadataFields.Spec spec;
    private final Combo combo;
    private final Composite nested;
    private final Map<String, String> idByLabel;
    private HMetadataForm nestedForm;
    private Object instance;

    private PluginBinding(
        HMetadataFields.Spec spec, Combo combo, Composite nested, Map<String, String> idByLabel) {
      this.spec = spec;
      this.combo = combo;
      this.nested = nested;
      this.idByLabel = idByLabel;
    }

    @Override
    public void load() {
      instance = HMetadataFields.get(bean, spec.field());
      String pluginId = pluginIdOf(instance);
      combo.setText("");
      for (Map.Entry<String, String> entry : idByLabel.entrySet()) {
        if (entry.getValue().equals(pluginId)) {
          combo.setText(entry.getKey());
          break;
        }
      }
      rebuildNested();
    }

    @Override
    public void save() {
      if (nestedForm != null) {
        nestedForm.getWidgetsContent();
      }
      HMetadataFields.set(bean, spec.field(), instance);
    }

    private void changeType() {
      String id = idByLabel.get(combo.getText());
      if (id == null) {
        return;
      }
      if (id.equals(pluginIdOf(instance))) {
        return;
      }
      instance = newPlugin(spec.type(), id);
      rebuildNested();
      onModify.run();
    }

    private void rebuildNested() {
      for (Control child : nested.getChildren()) {
        child.dispose();
      }
      nestedForm = null;
      if (instance != null) {
        nestedForm =
            new HMetadataForm(nested, instance, variables, metadataProvider, onModify, false);
        nestedForm.setWidgetsContent();
      }
      nested.layout(true, true);
      relayout();
    }
  }

  private final class ListBinding extends CollectionBinding {
    private final HMetadataFields.Spec spec;
    private final org.eclipse.swt.widgets.List list;
    private List<Object> items = new ArrayList<>();

    private ListBinding(HMetadataFields.Spec spec, org.eclipse.swt.widgets.List list) {
      this.spec = spec;
      this.list = list;
    }

    @Override
    public void load() {
      Object value = HMetadataFields.get(bean, spec.field());
      items = new ArrayList<>();
      if (value instanceof List<?> existing) {
        items.addAll(existing);
      }
      refreshList();
    }

    @Override
    public void save() {
      HMetadataFields.set(bean, spec.field(), new ArrayList<>(items));
    }

    @Override
    void add() {
      Class<?> elementType = spec.listElementType();
      if (elementType == String.class) {
        EnterStringDialog dialog =
            new EnterStringDialog(body.getShell(), "", spec.label(), spec.label());
        String value = dialog.open();
        if (value != null) {
          items.add(value);
          refreshList();
          onModify.run();
        }
        return;
      }
      if (elementType == HColorRGB.class) {
        HColorRGB chosen = pickColor("#008CC2");
        if (chosen != null) {
          items.add(chosen);
          refreshList();
          onModify.run();
        }
        return;
      }
      if (elementType == HFont.class) {
        HFont chosen = pickFont(new HFont("Arial", "12", false, false));
        if (chosen != null) {
          items.add(chosen);
          refreshList();
          onModify.run();
        }
        return;
      }
      Object created;
      if (HMetadataFields.isPluginInterface(elementType)) {
        created = pickPlugin(elementType);
      } else {
        created = HMetadataFields.newInstance(elementType);
      }
      if (created == null) {
        return;
      }
      if (HMetadataFields.isSimple(elementType) || editBean(body.getShell(), spec.label(), created)) {
        items.add(created);
        refreshList();
        onModify.run();
      }
    }

    @Override
    void edit() {
      int index = list.getSelectionIndex();
      if (index < 0 || index >= items.size()) {
        return;
      }
      Object item = items.get(index);
      if (item instanceof String string) {
        EnterStringDialog dialog =
            new EnterStringDialog(body.getShell(), string, spec.label(), spec.label());
        String value = dialog.open();
        if (value != null) {
          items.set(index, value);
          refreshList();
          onModify.run();
        }
        return;
      }
      if (item instanceof HColorRGB color) {
        HColorRGB chosen = pickColor(color.getHexColor());
        if (chosen != null) {
          items.set(index, chosen);
          refreshList();
          onModify.run();
        }
        return;
      }
      if (item instanceof HFont font) {
        HFont chosen = pickFont(font);
        if (chosen != null) {
          items.set(index, chosen);
          refreshList();
          onModify.run();
        }
        return;
      }
      if (item != null && editBean(body.getShell(), spec.label(), item)) {
        refreshList();
        onModify.run();
      }
    }

    @Override
    void remove() {
      int index = list.getSelectionIndex();
      if (index < 0 || index >= items.size()) {
        return;
      }
      items.remove(index);
      refreshList();
      onModify.run();
    }

    private void refreshList() {
      String[] labels = new String[items.size()];
      for (int i = 0; i < items.size(); i++) {
        labels[i] = HMetadataFields.labelOf(items.get(i), i);
      }
      list.setItems(labels);
    }
  }

  private final class MapBinding extends CollectionBinding {
    private final HMetadataFields.Spec spec;
    private final org.eclipse.swt.widgets.List list;
    private final List<String[]> entries = new ArrayList<>();

    private MapBinding(HMetadataFields.Spec spec, org.eclipse.swt.widgets.List list) {
      this.spec = spec;
      this.list = list;
    }

    @Override
    public void load() {
      entries.clear();
      Object value = HMetadataFields.get(bean, spec.field());
      if (value instanceof Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          entries.add(
              new String[] {
                entry.getKey() == null ? "" : String.valueOf(entry.getKey()),
                entry.getValue() == null ? "" : String.valueOf(entry.getValue())
              });
        }
      }
      refreshList();
    }

    @Override
    public void save() {
      Map<String, String> map = new LinkedHashMap<>();
      for (String[] entry : entries) {
        if (StringUtils.isNotBlank(entry[0])) {
          map.put(entry[0], entry[1] == null ? "" : entry[1]);
        }
      }
      HMetadataFields.set(bean, spec.field(), map);
    }

    @Override
    void add() {
      String[] created = editMapEntry("", "");
      if (created != null) {
        entries.add(created);
        refreshList();
        onModify.run();
      }
    }

    @Override
    void edit() {
      int index = list.getSelectionIndex();
      if (index < 0 || index >= entries.size()) {
        return;
      }
      String[] current = entries.get(index);
      String[] edited = editMapEntry(current[0], current[1]);
      if (edited != null) {
        entries.set(index, edited);
        refreshList();
        onModify.run();
      }
    }

    @Override
    void remove() {
      int index = list.getSelectionIndex();
      if (index < 0 || index >= entries.size()) {
        return;
      }
      entries.remove(index);
      refreshList();
      onModify.run();
    }

    private void refreshList() {
      String[] labels = new String[entries.size()];
      for (int i = 0; i < entries.size(); i++) {
        labels[i] = entries.get(i)[0] + " = " + entries.get(i)[1];
      }
      list.setItems(labels);
    }

    private String[] editMapEntry(String key, String value) {
      Shell shell = new Shell(body.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
      shell.setText(spec.label());
      shell.setLayout(new FormLayout());
      int margin = PropsUi.getMargin();
      Label keyLabel = new Label(shell, SWT.RIGHT);
      keyLabel.setText(BaseMessages.getString(PKG, "HMetadataForm.Key"));
      FormData fdKeyLabel = new FormData();
      fdKeyLabel.left = new FormAttachment(0, margin);
      fdKeyLabel.top = new FormAttachment(0, margin);
      keyLabel.setLayoutData(fdKeyLabel);
      Text keyText = new Text(shell, SWT.BORDER | SWT.SINGLE);
      keyText.setText(Const.NVL(key, ""));
      FormData fdKey = new FormData();
      fdKey.left = new FormAttachment(keyLabel, margin);
      fdKey.right = new FormAttachment(100, -margin);
      fdKey.top = new FormAttachment(keyLabel, 0, SWT.CENTER);
      fdKey.width = 280;
      keyText.setLayoutData(fdKey);
      Label valueLabel = new Label(shell, SWT.RIGHT);
      valueLabel.setText(BaseMessages.getString(PKG, "HMetadataForm.Value"));
      FormData fdValueLabel = new FormData();
      fdValueLabel.left = new FormAttachment(0, margin);
      fdValueLabel.top = new FormAttachment(keyText, margin);
      valueLabel.setLayoutData(fdValueLabel);
      Text valueText = new Text(shell, SWT.BORDER | SWT.SINGLE);
      valueText.setText(Const.NVL(value, ""));
      FormData fdValue = new FormData();
      fdValue.left = new FormAttachment(valueLabel, margin);
      fdValue.right = new FormAttachment(100, -margin);
      fdValue.top = new FormAttachment(valueLabel, 0, SWT.CENTER);
      valueText.setLayoutData(fdValue);
      Button ok = new Button(shell, SWT.PUSH);
      ok.setText(BaseMessages.getString(PKG, "HMetadataForm.Ok"));
      Button cancel = new Button(shell, SWT.PUSH);
      cancel.setText(BaseMessages.getString(PKG, "HMetadataForm.Cancel"));
      BaseTransformDialog.positionBottomButtons(shell, new Button[] {ok, cancel}, margin, valueText);
      final String[][] result = {null};
      ok.addListener(
          SWT.Selection,
          e -> {
            result[0] = new String[] {keyText.getText(), valueText.getText()};
            shell.dispose();
          });
      cancel.addListener(SWT.Selection, e -> shell.dispose());
      BaseDialog.defaultShellHandling(
          shell, c -> ok.notifyListeners(SWT.Selection, null), c -> shell.dispose());
      return result[0];
    }
  }

  private Object pickPlugin(Class<?> type) {
    Map<String, String> options = pluginOptions(type);
    if (options.isEmpty()) {
      return null;
    }
    Shell shell = new Shell(body.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
    shell.setText(BaseMessages.getString(PKG, "HMetadataForm.PluginType"));
    shell.setLayout(new FormLayout());
    int margin = PropsUi.getMargin();
    Label fieldLabel = new Label(shell, SWT.LEFT);
    fieldLabel.setText(BaseMessages.getString(PKG, "HMetadataForm.PluginType"));
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, margin);
    fdl.top = new FormAttachment(0, margin);
    fieldLabel.setLayoutData(fdl);
    Combo combo = new Combo(shell, SWT.BORDER | SWT.READ_ONLY);
    combo.setItems(options.keySet().toArray(String[]::new));
    if (!options.isEmpty()) {
      combo.select(0);
    }
    FormData fdCombo = new FormData();
    fdCombo.left = new FormAttachment(0, margin);
    fdCombo.right = new FormAttachment(100, -margin);
    fdCombo.top = new FormAttachment(fieldLabel, margin);
    fdCombo.width = 360;
    combo.setLayoutData(fdCombo);
    Button ok = new Button(shell, SWT.PUSH);
    ok.setText(BaseMessages.getString(PKG, "HMetadataForm.Ok"));
    Button cancel = new Button(shell, SWT.PUSH);
    cancel.setText(BaseMessages.getString(PKG, "HMetadataForm.Cancel"));
    BaseTransformDialog.positionBottomButtons(shell, new Button[] {ok, cancel}, margin, combo);
    final Object[] created = {null};
    ok.addListener(
        SWT.Selection,
        e -> {
          String id = options.get(combo.getText());
          created[0] = id == null ? null : newPlugin(type, id);
          shell.dispose();
        });
    cancel.addListener(SWT.Selection, e -> shell.dispose());
    BaseDialog.defaultShellHandling(
        shell, c -> ok.notifyListeners(SWT.Selection, null), c -> shell.dispose());
    return created[0];
  }

  static String pluginIdOf(Object instance) {
    if (instance instanceof IHConnector connector) {
      return connector.getPluginId();
    }
    if (instance instanceof IHComponent component) {
      return component.getPluginId();
    }
    return null;
  }

  static Object newPlugin(Class<?> type, String pluginId) {
    try {
      PluginRegistry registry = PluginRegistry.getInstance();
      if (type == IHConnector.class) {
        IPlugin plugin = registry.getPlugin(HConnectorPluginType.class, pluginId);
        return plugin == null ? null : registry.loadClass(plugin);
      }
      IPlugin plugin = registry.getPlugin(HComponentPluginType.class, pluginId);
      return plugin == null ? null : registry.loadClass(plugin);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create plugin " + pluginId, e);
    }
  }
}
