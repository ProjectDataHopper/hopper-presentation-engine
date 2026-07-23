package org.hopper.core.gui.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders a {@link GuiFormSchema} to HTML snippets compatible with hopper-presentation-rest's side-panel editor
 * ({@code initScript}, {@code loadScript}, {@code componentSaveScript}).
 */
public class GuiFormHtmlRenderer {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Render a component editor form (presentation side panel): presentation name, component name,
   * plugin fields, base/layout, save via {@code saveComponent()}.
   */
  public String render(GuiFormSchema schema) {
    StringBuilder html = new StringBuilder();
    html.append("<!-- Generated form for ").append(esc(schema.getPluginId())).append(" -->\n");

    // Sticky actions above widgets so Apply/Close stay reachable on long forms
    appendFormActionBar(
        html,
        "component",
        "saveComponent()",
        "closeComponent()",
        null);

    // Presentation name (read-only context)
    html.append("<label for=\"presentationName\">Presentation name: </label>\n");
    html.append(
        "<input type=\"text\" id=\"presentationName\" name=\"presentationName\" readonly style=\"background: lightgray\">\n");
    html.append("<br>\n\n");

    for (GuiFormSection section : schema.getSections()) {
      renderSection(html, section, schema);
    }

    html.append("\n<br>\n");
    appendFormActionBar(
        html,
        "component-bottom",
        "saveComponent()",
        "closeComponent()",
        null);

    renderInitScript(html, schema);
    renderLoadScript(html, schema);
    renderSaveScript(html, schema);

    return html.toString();
  }

  /**
   * Render a standalone connector metadata form (side panel): connector name, plugin fields.
   *
   * <p>Action bar: <strong>Apply</strong> refreshes sample preview ({@code
   * applyConnectorPreview()}), <strong>Save</strong> persists metadata ({@code saveConnector()}).
   * Field load/save reuses component helpers by aliasing {@code iComponent} to the nested connector
   * plugin payload. hopper-presentation-rest wraps this HTML in the connector studio shell (input/output panes).
   */
  public String renderConnector(GuiFormSchema schema) {
    StringBuilder html = new StringBuilder();
    html.append("<!-- Generated connector form for ")
        .append(esc(schema.getPluginId()))
        .append(" -->\n");

    appendConnectorFormActionBar(html, "connector");

    html.append("<label for=\"connectorName\">Connector name: </label>\n");
    html.append("<input type=\"text\" id=\"connectorName\" name=\"connectorName\">\n<br>\n");
    html.append("<label for=\"connectorVirtualPath\">Virtual path: </label>\n");
    html.append(
        "<input type=\"text\" id=\"connectorVirtualPath\" name=\"connectorVirtualPath\" "
            + "placeholder=\"e.g. demos/sales\" style=\"width:70%\">\n<br><br>\n");

    for (GuiFormSection section : schema.getSections()) {
      renderSection(html, section, schema);
    }

    html.append("\n<br>\n");
    appendConnectorFormActionBar(html, "connector-bottom");

    renderInitScript(html, schema);
    renderConnectorLoadScript(html, schema);
    renderConnectorSaveScript(html, schema);

    return html.toString();
  }

  /**
   * Apply / Close (and optional Back) actions for the side-panel editor.
   *
   * @param suffix unique id suffix so top and bottom bars do not clash
   * @param applyOnclick e.g. {@code saveComponent()}
   * @param closeOnclick e.g. {@code closeComponent()}
   * @param backOnclick optional third button (connector list); null to omit
   */
  private void appendFormActionBar(
      StringBuilder html,
      String suffix,
      String applyOnclick,
      String closeOnclick,
      String backOnclick) {
    html.append("<div class=\"form-action-bar\" id=\"formActionBar-")
        .append(esc(suffix))
        .append("\">\n");
    html.append("  <button type=\"button\" class=\"form-action-apply\" id=\"editButtonApply-")
        .append(esc(suffix))
        .append("\" onclick=\"")
        .append(applyOnclick)
        .append("\" title=\"Save changes and reload the presentation\">Apply</button>\n");
    html.append("  <button type=\"button\" class=\"form-action-close\" id=\"editButtonClose-")
        .append(esc(suffix))
        .append("\" onclick=\"")
        .append(closeOnclick)
        .append("\" title=\"Close the editor without saving\">Close</button>\n");
    if (StringUtils.isNotEmpty(backOnclick)) {
      html.append("  <button type=\"button\" class=\"form-action-back\" id=\"editButtonBack-")
          .append(esc(suffix))
          .append("\" onclick=\"")
          .append(backOnclick)
          .append("\" title=\"Return to the connector list\">Back to list</button>\n");
    }
    html.append("</div>\n\n");
  }

  /**
   * Connector studio action bar: Apply = preview samples, Save = persist, Close, Back to list.
   */
  private void appendConnectorFormActionBar(StringBuilder html, String suffix) {
    html.append("<div class=\"form-action-bar form-action-bar-connector\" id=\"formActionBar-")
        .append(esc(suffix))
        .append("\">\n");
    html.append(
        "  <button type=\"button\" class=\"form-action-apply\" id=\"editButtonApply-")
        .append(esc(suffix))
        .append(
            "\" onclick=\"applyConnectorPreview()\" "
                + "title=\"Refresh input/output sample rows from current form values "
                + "(does not save)\">Apply</button>\n");
    html.append(
        "  <button type=\"button\" class=\"form-action-save\" id=\"editButtonSave-")
        .append(esc(suffix))
        .append(
            "\" onclick=\"saveConnector()\" "
                + "title=\"Save connector metadata and reload the presentation\">Save</button>\n");
    html.append(
        "  <button type=\"button\" class=\"form-action-close\" id=\"editButtonClose-")
        .append(esc(suffix))
        .append("\" onclick=\"closeConnector()\" title=\"Close the editor without saving\">"
            + "Close</button>\n");
    html.append(
        "  <button type=\"button\" class=\"form-action-back\" id=\"editButtonBack-")
        .append(esc(suffix))
        .append(
            "\" onclick=\"editConnectorsList()\" title=\"Return to the connector list\">"
                + "Back to list</button>\n");
    html.append("</div>\n\n");
  }

  private void renderConnectorLoadScript(StringBuilder html, GuiFormSchema schema) {
    html.append("<script id=\"loadScript\">\n");
    html.append(
        """
        if (typeof connectorJson === "undefined" || connectorJson === null) {
          throw "connectorJson is not set";
        }
        let iComponent = connectorJson["connector"][connectorPluginId];
        if (iComponent === undefined || iComponent === null) {
          // Create empty payload for new/unknown shape
          iComponent = { "pluginId": connectorPluginId };
          if (!connectorJson["connector"]) { connectorJson["connector"] = {}; }
          connectorJson["connector"][connectorPluginId] = iComponent;
        }
        document.getElementById("connectorName").value = connectorJson["name"] || "";
        let vpEl = document.getElementById("connectorVirtualPath");
        if (vpEl) {
          vpEl.value = connectorJson["virtualPath"] || "";
        }
        """);
    if (hasListFields(schema)) {
      html.append(
          """
          let sourceConnectorName = iComponent["sourceConnectorName"];
          let sourceConnectorColumnNames = getConnectorColumnNames(sourceConnectorName);
          """);
    }
    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        appendLoad(html, field);
      }
    }
    html.append("</script>\n\n");
  }

  private void renderConnectorSaveScript(StringBuilder html, GuiFormSchema schema) {
    html.append("<script id=\"connectorSaveScript\">\n");
    html.append(
        """
        let iComponent = connectorJson["connector"][connectorPluginId];
        if (iComponent === undefined || iComponent === null) {
          iComponent = { "pluginId": connectorPluginId };
          if (!connectorJson["connector"]) { connectorJson["connector"] = {}; }
          connectorJson["connector"][connectorPluginId] = iComponent;
        }
        connectorJson["name"] = document.getElementById("connectorName").value;
        let vpSave = document.getElementById("connectorVirtualPath");
        if (vpSave) {
          connectorJson["virtualPath"] = (vpSave.value || "").trim();
        }
        iComponent["pluginId"] = connectorPluginId;
        """);
    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        appendSave(html, field);
      }
    }
    html.append("</script>\n");
  }

  private void renderSection(StringBuilder html, GuiFormSection section, GuiFormSchema schema) {
    if (HGuiFormConstants.SECTION_WRAPPER.equals(section.getId())) {
      // Flat fields at top (component name)
      for (GuiFormField field : section.getFields()) {
        renderSimpleField(html, field);
      }
      html.append("<br>\n");
      return;
    }

    String display = section.isOpenByDefault() ? "block" : "none";
    html.append("<button type=\"button\" class=\"collapsible\">")
        .append(esc(section.getTitle()))
        .append("</button>\n");
    html.append("<div class=\"content\" style=\"display: ").append(display).append("\">\n");

    // Layout shortcuts: one-click presets matching HLayout.fullPage() / topLeftPage()
    if (HGuiFormConstants.SECTION_LAYOUT.equals(section.getId())) {
      html.append("<div class=\"layout-shortcuts\">\n");
      html.append(
          "  <button type=\"button\" class=\"layout-shortcut-btn\" "
              + "onclick=\"if(typeof applyLayoutFullPage==='function')applyLayoutFullPage();\" "
              + "title=\"Left/top/right/bottom to page (0 offset)\">Full page</button>\n");
      html.append(
          "  <button type=\"button\" class=\"layout-shortcut-btn\" "
              + "onclick=\"if(typeof applyLayoutTopLeft==='function')applyLayoutTopLeft();\" "
              + "title=\"Left and top to page (0 offset)\">Top left</button>\n");
      html.append("</div>\n");
    }

    for (GuiFormField field : section.getFields()) {
      switch (field.getType()) {
        case LAYOUT_SIDE -> renderLayoutSide(html, field);
        case COLOR -> renderColorField(html, field);
        case FONT -> renderFontField(html, field);
        case SIZE -> renderSizeField(html, field);
        case LIST -> {
          if ("component".equals(field.getItemKind())) {
            renderComponentListField(html, field);
          } else if ("connector".equals(field.getItemKind())) {
            renderConnectorListField(html, field);
          } else {
            renderListField(html, field);
          }
        }
        case COMPONENT -> renderComponentField(html, field);
        default -> renderSimpleField(html, field);
      }
    }

    html.append("<br>\n</div>\n\n");
  }

  private void renderSimpleField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    switch (field.getType()) {
      case CHECKBOX -> {
        html.append("<input type=\"checkbox\" id=\"").append(id).append("\">\n");
        html.append("<label for=\"")
            .append(id)
            .append("\">")
            .append(esc(field.getLabel()))
            .append("</label>\n<br>\n");
      }
      case COMBO, METADATA -> {
        // Input connector: prominent row under component name with data + layout actions
        if ("sourceConnectorName".equals(field.getFieldName())
            || "sourceConnectorName".equals(field.getId())) {
          renderSourceConnectorField(html, field);
          return;
        }
        html.append("<label for=\"")
            .append(id)
            .append("\">")
            .append(esc(field.getLabel()))
            .append(" </label>\n");
        html.append("<select id=\"")
            .append(id)
            .append("\" style=\"width: 50%\"></select>\n<br>\n");
      }
      case MULTI_LINE_TEXT -> {
        html.append("<label for=\"")
            .append(id)
            .append("\">")
            .append(esc(field.getLabel()))
            .append(" </label>\n<br>\n");
        int rows = Math.max(1, field.getMultiLineTextHeight());
        html.append("<textarea id=\"")
            .append(id)
            .append("\" rows=\"")
            .append(rows)
            .append("\" style=\"width: 90%\"></textarea>\n<br>\n");
      }
      case PASSWORD -> {
        html.append("<label for=\"")
            .append(id)
            .append("\">")
            .append(esc(field.getLabel()))
            .append(" </label>\n");
        html.append("<input type=\"password\" id=\"").append(id).append("\">\n<br>\n");
      }
      case BUTTON -> renderButtonField(html, field);
      case LINK -> {
        // Reserved for future hyperlink fields
        html.append("<a href=\"#\" id=\"")
            .append(id)
            .append("\" onclick=\"if(typeof hopperFormButtonClick==='function'){hopperFormButtonClick('")
            .append(esc(field.getFieldName()))
            .append("');}return false;\">")
            .append(esc(field.getLabel()))
            .append("</a><br>\n");
      }
      default -> {
        html.append("<label for=\"")
            .append(id)
            .append("\">")
            .append(esc(field.getLabel()))
            .append(" </label>\n");
        html.append("<input type=\"text\" id=\"").append(id).append("\">\n<br>\n");
      }
    }
  }

  /**
   * Action button (no value binding). Invokes {@code hopperFormButtonClick(fieldName)} in hopper-presentation.
   */
  private void renderButtonField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String fieldName = esc(field.getFieldName());
    String label =
        StringUtils.isNotEmpty(field.getLabel()) ? field.getLabel() : field.getFieldName();
    html.append("<div class=\"form-field-button-row\">\n");
    html.append("  <button type=\"button\" class=\"form-field-btn\" id=\"")
        .append(id)
        .append("\"");
    if (StringUtils.isNotEmpty(field.getToolTip())) {
      html.append(" title=\"").append(esc(field.getToolTip())).append("\"");
    }
    html.append(" onclick=\"if(typeof hopperFormButtonClick==='function')hopperFormButtonClick('")
        .append(fieldName)
        .append("');\">")
        .append(esc(label))
        .append("</button>\n");
    html.append("</div>\n");
  }

  /**
   * Top-of-form input connector: combo + icon buttons to preview sample rows and field layout.
   * Handlers live in hopper-presentation-rest ({@code previewSourceConnectorData}, {@code
   * previewSourceConnectorLayout}).
   */
  private void renderSourceConnectorField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String label =
        StringUtils.isNotEmpty(field.getLabel()) ? field.getLabel() : "Input connector";
    html.append("<div class=\"source-connector-row\" id=\"sourceConnectorRow\">\n");
    html.append("  <label for=\"")
        .append(id)
        .append("\" class=\"source-connector-label\">")
        .append(esc(label))
        .append("</label>\n");
    html.append("  <select id=\"")
        .append(id)
        .append("\" class=\"source-connector-select\" style=\"width: 50%\"></select>\n");
    html.append("  <span class=\"source-connector-actions\">\n");
    html.append(
        "    <button type=\"button\" class=\"source-connector-btn\" id=\"sourceConnectorPreviewDataBtn\" "
            + "title=\"Preview sample data from this connector\" "
            + "onclick=\"if(typeof previewSourceConnectorData==='function')previewSourceConnectorData();\">\n");
    html.append(
        "      <img src=\"/hopper/api/static/images/connector-sample-data.svg\" "
            + "alt=\"Preview data\" width=\"18\" height=\"18\">\n");
    html.append("    </button>\n");
    html.append(
        "    <button type=\"button\" class=\"source-connector-btn\" id=\"sourceConnectorPreviewLayoutBtn\" "
            + "title=\"Show field layout (column names and types)\" "
            + "onclick=\"if(typeof previewSourceConnectorLayout==='function')previewSourceConnectorLayout();\">\n");
    html.append(
        "      <img src=\"/hopper/api/static/images/connector-metadata.svg\" "
            + "alt=\"Field layout\" width=\"18\" height=\"18\">\n");
    html.append("    </button>\n");
    html.append("  </span>\n");
    html.append("</div>\n");
    // Inline inspect panel (sample rows / layout) toggled by the icon buttons
    html.append(
        "<div id=\"sourceConnectorInspect\" class=\"source-connector-inspect\" hidden>\n");
    html.append(
        "  <div class=\"source-connector-inspect-header\">\n"
            + "    <span id=\"sourceConnectorInspectTitle\" class=\"source-connector-inspect-title\">"
            + "</span>\n"
            + "    <button type=\"button\" class=\"source-connector-inspect-close\" "
            + "title=\"Close\" "
            + "onclick=\"if(typeof closeSourceConnectorInspect==='function')closeSourceConnectorInspect();\">×</button>\n"
            + "  </div>\n");
    html.append(
        "  <div id=\"sourceConnectorInspectBody\" class=\"source-connector-inspect-body\">"
            + "</div>\n");
    html.append("</div>\n");
  }

  private void renderColorField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String setId = "set" + capitalize(field.getId());
    // Match existing hopper-presentation-rest conventions where possible
    if ("borderColor".equals(field.getFieldName())) {
      html.append("<input type=\"checkbox\" id=\"border\">\n");
      html.append("<label for=\"border\">Draw border? </label>\n");
      html.append("<input id=\"borderColor\" type=\"color\" style=\"width: 50%;border: none transparent\">\n<br>\n");
      return;
    }
    if ("backGroundColor".equals(field.getFieldName())) {
      html.append("<input type=\"checkbox\" id=\"background\">\n");
      html.append("<label for=\"background\">Draw background? </label>\n");
      html.append(
          "<input id=\"backGroundColor\" type=\"color\" style=\"width: 50%;border: none transparent\">\n<br>\n");
      return;
    }
    if ("defaultColor".equals(field.getFieldName())) {
      html.append("<input type=\"checkbox\" id=\"setDefaultColor\">\n");
      html.append("<label for=\"defaultColor\">Default color </label>\n");
      html.append(
          "<input id=\"defaultColor\" type=\"color\" style=\"width: 50%;border: none transparent\">\n<br>\n");
      return;
    }
    html.append("<input type=\"checkbox\" id=\"").append(setId).append("\">\n");
    html.append("<label for=\"")
        .append(id)
        .append("\">")
        .append(esc(field.getLabel()))
        .append(" </label>\n");
    html.append("<input id=\"")
        .append(id)
        .append("\" type=\"color\" style=\"width: 50%;border: none transparent\">\n<br>\n");
  }

  private void renderFontField(StringBuilder html, GuiFormField field) {
    if ("defaultFont".equals(field.getFieldName())) {
      html.append("<input type=\"checkbox\" id=\"setDefaultFont\">\n");
      html.append("<label for=\"defaultFontName\">Default font </label>\n");
      html.append("<input id=\"defaultFontName\" type=\"text\" style=\"width: 25%\">\n");
      html.append("<input id=\"defaultFontSize\" type=\"text\" style=\"width: 10%\">\n");
      html.append("<label for=\"defaultFontBold\">bold? </label>\n");
      html.append("<input id=\"defaultFontBold\" type=\"checkbox\">\n");
      html.append("<label for=\"defaultFontItalic\">italic? </label>\n");
      html.append("<input id=\"defaultFontItalic\" type=\"checkbox\">\n<br>\n");
      return;
    }
    String p = esc(field.getId());
    html.append("<input type=\"checkbox\" id=\"set").append(capitalize(p)).append("\">\n");
    html.append("<label>").append(esc(field.getLabel())).append(" </label>\n");
    html.append("<input id=\"").append(p).append("Name\" type=\"text\" style=\"width: 25%\">\n");
    html.append("<input id=\"").append(p).append("Size\" type=\"text\" style=\"width: 10%\">\n");
    html.append("<label>bold? </label><input id=\"").append(p).append("Bold\" type=\"checkbox\">\n");
    html.append("<label>italic? </label><input id=\"")
        .append(p)
        .append("Italic\" type=\"checkbox\">\n<br>\n");
  }

  private void renderComponentField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    html.append("<fieldset class=\"nested-component-fieldset\" style=\"border: 1px solid #777; margin: 8px 0; padding: 8px;\">\n");
    html.append("<legend>").append(esc(field.getLabel())).append("</legend>\n");
    html.append("<div id=\"")
        .append(id)
        .append("_panel\" class=\"nested-component-panel\" data-prefix=\"")
        .append(id)
        .append("\" data-field=\"")
        .append(esc(field.getFieldName()))
        .append("\">\n");
    html.append("  <!-- filled by setNestedComponent() -->\n");
    html.append("</div>\n");
    html.append("</fieldset>\n");
  }

  private void renderComponentListField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    html.append("<fieldset class=\"nested-component-list-fieldset\" style=\"border: 1px solid #777; margin: 8px 0; padding: 8px;\">\n");
    html.append("<legend>").append(esc(field.getLabel())).append("</legend>\n");
    html.append("<div id=\"")
        .append(id)
        .append("_items\" class=\"nested-component-list\" data-prefix=\"")
        .append(id)
        .append("\" data-field=\"")
        .append(esc(field.getFieldName()))
        .append("\"></div>\n");
    html.append("<button type=\"button\" id=\"")
        .append(id)
        .append("_add\" onclick=\"nestedComponentListAdd('")
        .append(id)
        .append("')\">Add child component</button>\n");
    html.append("</fieldset>\n");
  }

  /**
   * Ordered chain of nested connectors (e.g. {@code ChainConnector.connectors}). Client fills
   * steps from {@code window.connectorCatalog}.
   */
  private void renderConnectorListField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    html.append(
        "<fieldset class=\"nested-connector-list-fieldset\" "
            + "style=\"border: 1px solid #777; margin: 8px 0; padding: 8px;\">\n");
    html.append("<legend>").append(esc(field.getLabel())).append("</legend>\n");
    if (StringUtils.isNotEmpty(field.getToolTip())) {
      html.append("<p class=\"editor-hint\">")
          .append(esc(field.getToolTip()))
          .append("</p>\n");
    }
    html.append("<div id=\"")
        .append(id)
        .append("_items\" class=\"nested-connector-list\" data-prefix=\"")
        .append(id)
        .append("\" data-field=\"")
        .append(esc(field.getFieldName()))
        .append("\"></div>\n");
    html.append("<button type=\"button\" class=\"nested-connector-add-btn\" id=\"")
        .append(id)
        .append("_add\" onclick=\"nestedConnectorListAdd('")
        .append(id)
        .append("')\">Add step</button>\n");
    html.append("</fieldset>\n");
  }

  private void renderListField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String kind = StringUtils.defaultIfEmpty(field.getItemKind(), "column");
    // Header label with Add toolbar (works even when the table is empty).
    // Delete stays on each data row (last column).
    html.append("<div class=\"list-field-header\">\n");
    html.append("<label for=\"")
        .append(id)
        .append("\">")
        .append(esc(field.getLabel()))
        .append("</label>\n");
    html.append("<span class=\"list-field-toolbar\">\n");
    html.append("<button type=\"button\" class=\"list-toolbar-btn\" id=\"")
        .append(id)
        .append("_add\" title=\"Add row\" onclick=\"listFieldAdd('")
        .append(id)
        .append("')\">");
    html.append(
        "<img src=\"/hopper/api/static/images/add-item.svg\" alt=\"Add\" width=\"16\" height=\"16\">");
    html.append("</button>\n");
    html.append("</span>\n</div>\n");

    html.append("<table id=\"")
        .append(id)
        .append("\" class=\"list-field-table\" data-list-kind=\"")
        .append(esc(kind))
        .append("\" data-column-prefix=\"")
        .append(id)
        .append("\">\n<tr>\n");
    if ("fact".equals(kind)) {
      html.append(
          """
          <th>Column name</th>
          <th>Header</th>
          <th>Width</th>
          <th>H-Align</th>
          <th>V-align</th>
          <th>Format</th>
          <th>H-Agg</th>
          <th>V-Agg</th>
          <th>Method</th>
          <th></th>
          <th></th>
          <th></th>
          """);
    } else if ("string".equals(kind)) {
      html.append("<th>Value</th><th></th><th></th><th></th>\n");
    } else if ("sort".equals(kind)) {
      html.append("<th>Type</th><th>Ascending</th><th></th><th></th><th></th>\n");
    } else if ("filter".equals(kind)) {
      html.append("<th>Field name</th><th>Filter value</th><th></th><th></th><th></th>\n");
    } else if ("groupKey".equals(kind)) {
      html.append(
          "<th>Group column</th><th>Connector column</th><th></th><th></th><th></th>\n");
    } else if ("jsonField".equals(kind)) {
      html.append(
          """
          <th>JSON tag</th>
          <th>Name</th>
          <th>Type</th>
          <th>Format</th>
          <th>Length</th>
          <th>Precision</th>
          <th></th>
          <th></th>
          <th></th>
          """);
    } else if ("csvField".equals(kind)) {
      html.append(
          """
          <th>Name</th>
          <th>Type</th>
          <th>Format</th>
          <th>Length</th>
          <th>Precision</th>
          <th></th>
          <th></th>
          <th></th>
          """);
    } else if ("connector".equals(kind) || "bean".equals(kind)) {
      html.append("<th>Plugin JSON (advanced)</th><th></th><th></th><th></th>\n");
    } else {
      // column / dimension
      html.append(
          """
          <th>Column name</th>
          <th>Header value</th>
          <th>Width</th>
          <th>H-Align</th>
          <th>V-align</th>
          <th>Format</th>
          <th></th>
          <th></th>
          <th></th>
          """);
    }
    html.append("</tr>\n</table>\n<br>\n");
  }

  private void renderSizeField(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    html.append("<label>")
        .append(esc(field.getLabel()))
        .append(" </label>\n");
    html.append("<label for=\"")
        .append(id)
        .append("Width\">Width </label>\n");
    html.append("<input type=\"text\" id=\"")
        .append(id)
        .append("Width\" style=\"width: 15%\">\n");
    html.append("<label for=\"")
        .append(id)
        .append("Height\"> Height </label>\n");
    html.append("<input type=\"text\" id=\"")
        .append(id)
        .append("Height\" style=\"width: 15%\">\n<br>\n");
  }

  private void renderLayoutSide(StringBuilder html, GuiFormField field) {
    String side = field.getFieldName(); // left/right/top/bottom
    String cap = capitalize(side);
    html.append("<fieldset class=\"layout-side-fieldset\" data-layout-side=\"")
        .append(side)
        .append("\" style=\"border-width: 1px;border-color: #777777\">\n");
    html.append("  <Legend>").append(cap).append(" alignment</Legend>\n");
    html.append("  <label for=\"")
        .append(side)
        .append("Enabled\">")
        .append(cap)
        .append(" aligned</label>");
    html.append("<input type=\"checkbox\" id=\"")
        .append(side)
        .append("Enabled\">\n");
    html.append("  <label for=\"")
        .append(side)
        .append("ObjectName\"> Relative to component<br><span style=\"font-size:11px;color:#555\">")
        .append("(empty = relative to page)</span></label>\n");
    html.append("  <select id=\"")
        .append(side)
        .append("ObjectName\" style=\"width: 50%\" title=\"Leave empty to attach relative to the page edge\"></select><br>\n");
    html.append("  <label for=\"")
        .append(side)
        .append("Offset\">Offset (px): </label>");
    html.append("<input type=\"text\" id=\"")
        .append(side)
        .append("Offset\" style=\"width: 20%\" title=\"Pixels from the chosen edge\">\n");
    html.append("  <label for=\"")
        .append(side)
        .append("Percentage\">Percentage: </label>");
    html.append("<input type=\"text\" id=\"")
        .append(side)
        .append("Percentage\" style=\"width: 20%\">\n");
    html.append("  <label for=\"")
        .append(side)
        .append("Alignment\">From edge </label>");
    html.append("<select id=\"")
        .append(side)
        .append("Alignment\" style=\"width: 20%\" title=\"Which edge of the page or reference component\"></select><br>\n");
    html.append("  <p class=\"layout-side-hint editor-hint\" id=\"")
        .append(side)
        .append("LayoutHint\" style=\"margin:4px 0 0 0;font-size:11px;color:#345\"></p>\n");
    html.append("</fieldset>\n");
  }

  private void renderInitScript(StringBuilder html, GuiFormSchema schema) {
    html.append("<script id=\"initScript\">\n");
    if (schema.getComponentCatalog() != null && !schema.getComponentCatalog().isEmpty()) {
      try {
        String catalogJson = MAPPER.writeValueAsString(schema.getComponentCatalog());
        html.append("window.componentCatalog = ")
            .append(catalogJson)
            .append(";\n");
      } catch (Exception e) {
        html.append("window.componentCatalog = [];\n");
      }
    } else {
      html.append("window.componentCatalog = window.componentCatalog || [];\n");
    }

    if (schema.getConnectorCatalog() != null && !schema.getConnectorCatalog().isEmpty()) {
      try {
        String catalogJson = MAPPER.writeValueAsString(schema.getConnectorCatalog());
        html.append("window.connectorCatalog = ")
            .append(catalogJson)
            .append(";\n");
      } catch (Exception e) {
        html.append("window.connectorCatalog = [];\n");
      }
    } else {
      html.append("window.connectorCatalog = window.connectorCatalog || [];\n");
    }

    // Ensure presentation metadata caches are warm before binding selects
    html.append(
        "if (typeof ensureFormMetadataCaches === 'function') { ensureFormMetadataCaches(); }\n");

    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        if (field.getType() == GuiFormFieldType.COMBO
            || field.getType() == GuiFormFieldType.METADATA) {
          appendComboInit(html, field);
        }
        if (field.getType() == GuiFormFieldType.LAYOUT_SIDE) {
          String side = field.getFieldName();
          html.append("bindSelectSource('")
              .append(side)
              .append("ObjectName', 'components');\n");
          // HAttachment.Alignment (CENTER for vertical), not content HVerticalAlignment (MIDDLE)
          if ("left".equals(side) || "right".equals(side)) {
            html.append("setSelectOptions(\"")
                .append(side)
                .append("Alignment\", LAYOUT_HORIZONTAL_ALIGNMENTS);\n");
          } else {
            html.append("setSelectOptions(\"")
                .append(side)
                .append("Alignment\", LAYOUT_VERTICAL_ALIGNMENTS);\n");
          }
        }
        if (field.getType() == GuiFormFieldType.LIST
            && ("column".equals(field.getItemKind())
                || "fact".equals(field.getItemKind())
                || "filter".equals(field.getItemKind()))) {
          // Column/fact/filter tables use connector columns; refresh when source changes
          html.append("registerConnectorColumnListTable('")
              .append(esc(field.getId()))
              .append("', '")
              .append(esc(StringUtils.defaultIfEmpty(field.getComboDependsOn(), "sourceConnectorName")))
              .append("', '")
              .append(esc(StringUtils.defaultIfEmpty(field.getItemKind(), "column")))
              .append("');\n");
        }
        if (field.getType() == GuiFormFieldType.COMPONENT) {
          html.append("initNestedComponentPanel('")
              .append(esc(field.getId()))
              .append("');\n");
        }
        if (field.getType() == GuiFormFieldType.LIST && "component".equals(field.getItemKind())) {
          html.append("initNestedComponentList('")
              .append(esc(field.getId()))
              .append("');\n");
        }
        if (field.getType() == GuiFormFieldType.LIST && "connector".equals(field.getItemKind())) {
          html.append("initNestedConnectorList('")
              .append(esc(field.getId()))
              .append("');\n");
        }
      }
    }
    html.append(
        """
        if (typeof wireConnectorDependentCombos === 'function') { wireConnectorDependentCombos(); }
        $('.collapsible').click(function () {
                let c = $(this).next();
                if (c.css('display') === "block") {
                    c.css('display', 'none');
                } else {
                    c.css('display', 'block');
                }
            }
        );
        """);
    html.append("</script>\n\n");
  }

  private void appendComboInit(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String source = StringUtils.defaultIfEmpty(field.getComboSource(), "none");
    List<String> values = field.getComboValues();
    if ("none".equals(source) && values != null && !values.isEmpty()) {
      html.append("setSelectOptions('").append(id).append("', ").append(toJsArray(values)).append(");\n");
      return;
    }
    if ("none".equals(source) || StringUtils.isEmpty(source)) {
      // still try enum-like static values
      if (values != null && !values.isEmpty()) {
        html.append("setSelectOptions('").append(id).append("', ").append(toJsArray(values)).append(");\n");
      }
      return;
    }
    String depends =
        StringUtils.defaultIfEmpty(field.getComboDependsOn(), "sourceConnectorName");
    String metaKey = StringUtils.defaultString(field.getMetadataKey());
    html.append("bindSelectSource('")
        .append(id)
        .append("', '")
        .append(esc(source))
        .append("', { dependsOn: '")
        .append(esc(depends))
        .append("', metadataKey: '")
        .append(esc(metaKey))
        .append("', staticValues: ")
        .append(values != null && !values.isEmpty() ? toJsArray(values) : "[]")
        .append(" });\n");
  }

  private void renderLoadScript(StringBuilder html, GuiFormSchema schema) {
    html.append("<script id=\"loadScript\">\n");
    html.append("let iComponent = componentJson[\"component\"][componentPluginId];\n");
    html.append("document.getElementById(\"presentationName\").value = presentationName;\n");
    if (hasListFields(schema)) {
      html.append(
          """
          let sourceConnectorName = iComponent["sourceConnectorName"];
          let sourceConnectorColumnNames = getConnectorColumnNames(sourceConnectorName);
          """);
    }

    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        appendLoad(html, field);
      }
    }
    html.append("</script>\n\n");
  }

  private boolean hasListFields(GuiFormSchema schema) {
    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        if (field.getType() == GuiFormFieldType.LIST
            && !"component".equals(field.getItemKind())
            && !"connector".equals(field.getItemKind())) {
          return true;
        }
      }
    }
    return false;
  }

  private void appendLoad(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String jsonId = esc(field.getFieldName());
    String target = "wrapper".equals(field.getBinding()) ? "componentJson" : "iComponent";

    switch (field.getType()) {
      case CHECKBOX ->
          html.append("setChecked(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
      case COLOR -> appendColorLoad(html, field);
      case FONT -> {
        String setId = "defaultFont".equals(field.getFieldName()) ? "setDefaultFont" : "set" + capitalize(field.getFieldName());
        String prefix = field.getFieldName();
        if ("defaultFont".equals(field.getFieldName())) {
          prefix = "defaultFont";
        }
        html.append("setFont(iComponent, \"")
            .append(jsonId)
            .append("\", \"")
            .append(setId)
            .append("\", \"")
            .append(prefix)
            .append("\");\n");
      }
      case LIST -> {
        String kind = StringUtils.defaultIfEmpty(field.getItemKind(), "column");
        String prefix = id;
        if ("component".equals(kind)) {
          html.append("setNestedComponentList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("fact".equals(kind)) {
          html.append("setFacts(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\", \"")
              .append(prefix)
              .append("\", sourceConnectorColumnNames);\n");
        } else if ("string".equals(kind)) {
          html.append("setStringList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("sort".equals(kind)) {
          html.append("setSortMethods(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("filter".equals(kind)) {
          html.append("setFilterValues(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\", sourceConnectorColumnNames);\n");
        } else if ("groupKey".equals(kind)) {
          html.append("setGroupKeyMappings(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("jsonField".equals(kind)) {
          html.append("setJsonFields(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("csvField".equals(kind)) {
          html.append("setCsvFields(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("connector".equals(kind)) {
          html.append("setNestedConnectorList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("bean".equals(kind)) {
          html.append("setJsonObjectList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else {
          html.append("setColumns(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\", \"")
              .append(prefix)
              .append("\", sourceConnectorColumnNames);\n");
        }
      }
      case COMPONENT ->
          html.append("setNestedComponent(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
      case LAYOUT_SIDE ->
          html.append("setLayout(componentJson, \"").append(jsonId).append("\");\n");
      case SIZE ->
          html.append("setSize(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
      case BUTTON, LINK -> {
        // no value binding
      }
      default -> {
        if ("name".equals(field.getFieldName()) && "wrapper".equals(field.getBinding())) {
          html.append("setElement(componentJson, \"componentName\", \"name\");\n");
        } else {
          html.append("setElement(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
        }
      }
    }
  }

  private void appendColorLoad(StringBuilder html, GuiFormField field) {
    String jsonId = field.getFieldName();
    if ("borderColor".equals(jsonId)) {
      html.append(
          "setColor(iComponent, \"borderColor\", \"border\", \"borderColor\", \"#000000\");\n");
    } else if ("backGroundColor".equals(jsonId)) {
      html.append(
          "setColor(iComponent, \"backGroundColor\", \"background\", \"backGroundColor\", \"#ffffff\");\n");
    } else if ("defaultColor".equals(jsonId)) {
      html.append(
          "setColor(iComponent, \"defaultColor\", \"setDefaultColor\", \"defaultColor\", \"#ffffff\");\n");
    } else {
      String setId = "set" + capitalize(jsonId);
      html.append("setColor(iComponent, \"")
          .append(jsonId)
          .append("\", \"")
          .append(setId)
          .append("\", \"")
          .append(jsonId)
          .append("\", \"#000000\");\n");
    }
  }

  private void renderSaveScript(StringBuilder html, GuiFormSchema schema) {
    html.append("<script id=\"componentSaveScript\">\n");
    html.append("let iComponent = componentJson[\"component\"][componentPluginId];\n");

    for (GuiFormSection section : schema.getSections()) {
      for (GuiFormField field : section.getFields()) {
        appendSave(html, field);
      }
    }
    html.append("</script>\n");
  }

  private void appendSave(StringBuilder html, GuiFormField field) {
    String id = esc(field.getId());
    String jsonId = esc(field.getFieldName());
    String target = "wrapper".equals(field.getBinding()) ? "componentJson" : "iComponent";

    switch (field.getType()) {
      case CHECKBOX ->
          html.append("getChecked(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
      case COLOR -> appendColorSave(html, field);
      case FONT -> {
        String setId =
            "defaultFont".equals(field.getFieldName())
                ? "setDefaultFont"
                : "set" + capitalize(field.getFieldName());
        String prefix = field.getFieldName();
        html.append("getFont(iComponent, \"")
            .append(jsonId)
            .append("\", \"")
            .append(setId)
            .append("\", \"")
            .append(prefix)
            .append("\");\n");
      }
      case LIST -> {
        String kind = StringUtils.defaultIfEmpty(field.getItemKind(), "column");
        if ("component".equals(kind)) {
          html.append("getNestedComponentList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("fact".equals(kind)) {
          html.append("getFacts(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("string".equals(kind)) {
          html.append("getStringList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("sort".equals(kind)) {
          html.append("getSortMethods(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("filter".equals(kind)) {
          html.append("getFilterValues(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("groupKey".equals(kind)) {
          html.append("getGroupKeyMappings(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("jsonField".equals(kind)) {
          html.append("getJsonFields(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("csvField".equals(kind)) {
          html.append("getCsvFields(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("connector".equals(kind)) {
          html.append("getNestedConnectorList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else if ("bean".equals(kind)) {
          html.append("getJsonObjectList(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        } else {
          html.append("getColumns(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
        }
      }
      case COMPONENT ->
          html.append("getNestedComponent(iComponent, \"")
              .append(jsonId)
              .append("\", \"")
              .append(id)
              .append("\");\n");
      case LAYOUT_SIDE ->
          html.append("getLayout(componentJson, \"").append(jsonId).append("\");\n");
      case SIZE ->
          html.append("getSize(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
      case BUTTON, LINK -> {}
      default -> {
        if ("name".equals(field.getFieldName()) && "wrapper".equals(field.getBinding())) {
          html.append(
              "componentJson[\"name\"] = document.getElementById(\"componentName\").value;\n");
        } else if (field.isIntegerValue()) {
          html.append("getElementInteger(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
        } else {
          html.append("getElement(")
              .append(target)
              .append(", \"")
              .append(id)
              .append("\", \"")
              .append(jsonId)
              .append("\");\n");
        }
      }
    }
  }

  private void appendColorSave(StringBuilder html, GuiFormField field) {
    String jsonId = field.getFieldName();
    if ("borderColor".equals(jsonId)) {
      html.append("getColor(iComponent, \"borderColor\", \"border\", \"borderColor\");\n");
    } else if ("backGroundColor".equals(jsonId)) {
      html.append(
          "getColor(iComponent, \"backGroundColor\", \"background\", \"backGroundColor\");\n");
    } else if ("defaultColor".equals(jsonId)) {
      html.append(
          "getColor(iComponent, \"defaultColor\", \"setDefaultColor\", \"defaultColor\");\n");
    } else {
      String setId = "set" + capitalize(jsonId);
      html.append("getColor(iComponent, \"")
          .append(jsonId)
          .append("\", \"")
          .append(setId)
          .append("\", \"")
          .append(jsonId)
          .append("\");\n");
    }
  }

  private String toJsArray(List<String> values) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("'").append(values.get(i).replace("'", "\\'")).append("'");
    }
    sb.append("]");
    return sb.toString();
  }

  private static String esc(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  private static String capitalize(String s) {
    if (StringUtils.isEmpty(s)) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
