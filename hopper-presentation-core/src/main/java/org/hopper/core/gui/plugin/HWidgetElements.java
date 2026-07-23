package org.hopper.core.gui.plugin;

import java.lang.reflect.Field;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Runtime descriptor for one {@link HWidgetElement} on a plugin field.
 */
@Getter
@Setter
@NoArgsConstructor
public class HWidgetElements implements Comparable<HWidgetElements> {

  private String id;
  private String order;
  private String parentId;
  private HWidgetType type;
  private String label;
  private String toolTip;
  private String tabName;
  private String tabTooltip;
  private String fieldName;
  private Class<?> fieldClass;
  private String getterMethod;
  private String setterMethod;
  private String comboValuesMethod;
  private HComboSource comboSource = HComboSource.NONE;
  private String dependsOn;
  private String metadataKey;
  private boolean password;
  private boolean variablesEnabled = true;
  private boolean ignored;
  private boolean separator;
  private int multiLineTextHeight = 1;
  private Field field;
  private Class<?> ownerClass;

  public HWidgetElements(HWidgetElement annotation, Field field, Class<?> ownerClass) {
    this.field = field;
    this.ownerClass = ownerClass;
    this.fieldName = field.getName();
    this.fieldClass = field.getType();
    this.id = StringUtils.isEmpty(annotation.id()) ? field.getName() : annotation.id();
    this.order = annotation.order();
    this.parentId = annotation.parentId();
    this.type = annotation.type();
    this.label = annotation.label();
    this.toolTip = annotation.toolTip();
    this.tabName = annotation.tabName();
    this.tabTooltip = annotation.tabTooltip();
    this.password = annotation.password();
    this.variablesEnabled = annotation.variables();
    this.getterMethod = annotation.getterMethod();
    this.setterMethod = annotation.setterMethod();
    this.comboValuesMethod = annotation.comboValuesMethod();
    this.comboSource =
        annotation.comboSource() == null ? HComboSource.NONE : annotation.comboSource();
    this.dependsOn = annotation.dependsOn();
    this.metadataKey = annotation.metadataKey();
    this.ignored = annotation.ignored();
    this.separator = annotation.separator();
    this.multiLineTextHeight = Math.max(1, annotation.multiLineTextHeight());
  }

  @Override
  public int compareTo(HWidgetElements o) {
    int byOrder =
        StringUtils.defaultString(this.order).compareTo(StringUtils.defaultString(o.order));
    if (byOrder != 0) {
      return byOrder;
    }
    return StringUtils.defaultString(this.id).compareTo(StringUtils.defaultString(o.id));
  }
}
