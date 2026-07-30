package org.hopper.core.gui.form;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuiFormFilenameBrowseTest {

  @Test
  void filenameFieldRendersBrowseButton() {
    GuiFormSchema schema = new GuiFormSchema("HPipelineConnector", "Hop pipeline");
    GuiFormSection section = new GuiFormSection("plugin", "Plugin", true);
    GuiFormField file =
        new GuiFormField("pipelineFilename", GuiFormFieldType.FILENAME, "Pipeline filename", "pipelineFilename");
    GuiFormField run =
        new GuiFormField("runConfiguration", GuiFormFieldType.TEXT, "Run configuration", "runConfiguration");
    section.setFields(List.of(file, run));
    schema.setSections(List.of(section));

    String html = new GuiFormHtmlRenderer().renderConnector(schema);
    assertTrue(html.contains("hopperBrowseHopProject"), html);
    assertTrue(html.contains("Browse…"), html);
    assertTrue(html.contains("data-hop-browse=\"pipeline\""), html);
    assertTrue(html.contains("data-hop-run-config=\"true\""), html);
    assertTrue(html.contains("hopperRefreshHopRunConfigs"), html);
  }

  @Test
  void workflowPluginBrowseTypeIsWorkflow() {
    GuiFormSchema schema = new GuiFormSchema("HWorkflowComponent", "Hop Workflow");
    GuiFormSection section = new GuiFormSection("plugin", "Plugin", true);
    GuiFormField file =
        new GuiFormField("filename", GuiFormFieldType.FILENAME, "Workflow filename", "filename");
    section.setFields(List.of(file));
    schema.setSections(List.of(section));

    String html = new GuiFormHtmlRenderer().render(schema);
    assertTrue(html.contains("data-hop-browse=\"workflow\""), html);
  }
}
