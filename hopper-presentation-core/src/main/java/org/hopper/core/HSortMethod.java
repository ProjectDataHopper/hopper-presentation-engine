package org.hopper.core;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;

@Getter
@Setter
public class HSortMethod {

  @HWidgetElement(
      order = "100-type",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Sort type")
  @HopMetadataProperty
  private Type type;

  @HWidgetElement(
      order = "200-ascending",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Ascending?")
  @HopMetadataProperty
  private boolean ascending;

  @HopMetadataProperty private List<String> customOrder;

  public HSortMethod() {
    type = Type.NATIVE_VALUE;
    ascending = true;
    customOrder = new ArrayList<>();
  }

  public HSortMethod(Type type, boolean ascending) {
    this();
    this.type = type;
    this.ascending = ascending;
  }

  public HSortMethod(Type type, boolean ascending, List<String> customOrder) {
    this.type = type;
    this.ascending = ascending;
    this.customOrder = customOrder;
  }

  public HSortMethod(HSortMethod m) {
    this();
    this.type = m.type;
    this.ascending = m.ascending;
    this.customOrder.addAll(m.customOrder);
  }

  public enum Type {
    NATIVE_VALUE,
    STRING_ALPHA,
    STRING_ALPHA_CASE_INSENSITIVE,
    STRING_NUMERIC,
    STRING_CUSTOM
  }
}
