package org.hopper.core.gui.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A collapsible section of a configuration form. */
@Getter
@Setter
@NoArgsConstructor
public class GuiFormSection {
  private String id;
  private String title;
  private boolean openByDefault;
  private List<GuiFormField> fields = new ArrayList<>();

  public GuiFormSection(String id, String title, boolean openByDefault) {
    this.id = id;
    this.title = title;
    this.openByDefault = openByDefault;
  }
}
