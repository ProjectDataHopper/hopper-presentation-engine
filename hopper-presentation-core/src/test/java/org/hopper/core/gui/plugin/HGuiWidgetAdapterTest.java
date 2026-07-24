package org.hopper.core.gui.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.variables.resolver.VariableResolver;
import org.junit.jupiter.api.Test;
import org.hopper.core.gui.form.GuiFormSchema;
import org.hopper.core.gui.form.GuiFormSchemaBuilder;

class HGuiWidgetAdapterTest {

  /** Fixture mirroring BaseVaultVariableResolver-style Hop annotations. */
  public static class FakeVaultResolver {
    @GuiWidgetElement(
        id = "vaultAddress",
        order = "10",
        label = "Vault address",
        type = GuiElementType.TEXT,
        parentId = VariableResolver.GUI_PLUGIN_ELEMENT_PARENT_ID)
    protected String vaultAddress;

    @GuiWidgetElement(
        id = "vaultToken",
        order = "20",
        label = "Vault token",
        type = GuiElementType.TEXT,
        password = true,
        parentId = VariableResolver.GUI_PLUGIN_ELEMENT_PARENT_ID)
    protected String vaultToken;

    @GuiWidgetElement(
        id = "verifyingSsl",
        order = "40",
        label = "Verify SSL",
        type = GuiElementType.CHECKBOX,
        parentId = VariableResolver.GUI_PLUGIN_ELEMENT_PARENT_ID)
    protected boolean verifyingSsl;
  }

  @Test
  void mapsGuiWidgetElementToHWidgetElements() throws Exception {
    Field token = FakeVaultResolver.class.getDeclaredField("vaultToken");
    GuiWidgetElement ann = token.getAnnotation(GuiWidgetElement.class);
    HWidgetElements adapted =
        HGuiWidgetAdapter.fromGuiWidgetElement(ann, token, FakeVaultResolver.class);
    assertNotNull(adapted);
    assertEquals("vaultToken", adapted.getId());
    assertEquals(HWidgetType.TEXT, adapted.getType());
    assertTrue(adapted.isPassword());
    assertEquals(VariableResolver.GUI_PLUGIN_ELEMENT_PARENT_ID, adapted.getParentId());
  }

  @Test
  void registryRegistersHopGuiWidgets() {
    HGuiRegistry registry = HGuiRegistry.getInstance();
    registry.registerClass(FakeVaultResolver.class);
    Map<String, List<HWidgetElements>> byParent =
        registry.getElementsByParent(FakeVaultResolver.class);
    assertTrue(byParent.containsKey(VariableResolver.GUI_PLUGIN_ELEMENT_PARENT_ID));
    List<HWidgetElements> fields = byParent.get(VariableResolver.GUI_PLUGIN_ELEMENT_PARENT_ID);
    assertTrue(fields.stream().anyMatch(w -> "vaultAddress".equals(w.getId())));
    assertTrue(fields.stream().anyMatch(w -> "vaultToken".equals(w.getId()) && w.isPassword()));
    assertTrue(fields.stream().anyMatch(w -> "verifyingSsl".equals(w.getId())));
  }

  @Test
  void schemaBuilderIncludesAdaptedFields() {
    HGuiRegistry.getInstance().registerClass(FakeVaultResolver.class);
    GuiFormSchema schema =
        new GuiFormSchemaBuilder()
            .buildClassSchema(
                "Vault-Variable-Resolver",
                "Hashicorp Vault Variable Resolver",
                "test",
                FakeVaultResolver.class);
    assertTrue(schema.isHasPluginWidgets());
    assertTrue(
        schema.getSections().stream()
            .flatMap(s -> s.getFields().stream())
            .anyMatch(f -> "vaultToken".equals(f.getId()) || "vaultToken".equals(f.getFieldName())));
  }

  @Test
  void mapTypeCoversCoreTypes() {
    assertEquals(HWidgetType.TEXT, HGuiWidgetAdapter.mapType(GuiElementType.TEXT));
    assertEquals(HWidgetType.CHECKBOX, HGuiWidgetAdapter.mapType(GuiElementType.CHECKBOX));
    assertEquals(HWidgetType.FILENAME, HGuiWidgetAdapter.mapType(GuiElementType.FILENAME));
    assertEquals(HWidgetType.NONE, HGuiWidgetAdapter.mapType(GuiElementType.COMPOSITE));
  }

  @Test
  void resolveI18nDoesNotReturnRawI18nPrefix() {
    // Same shape as GooleSecretManagerVariableResolver labels. In core module classpath the google
    // bundle may be absent; we must never surface the raw i18n:… string to the admin UI.
    String raw =
        "i18n:org.apache.hop.core.variables.resolver:GooleSecretManagerVariableResolver.label.ProjectId";
    String resolved =
        HGuiWidgetAdapter.resolveI18n(
            raw, "org.apache.hop.core.variables.resolver", FakeVaultResolver.class);
    assertNotNull(resolved);
    assertFalse(resolved.startsWith("i18n:"), "must not return raw i18n string: " + resolved);
    assertFalse(
        resolved.startsWith("!") && resolved.endsWith("!"),
        "must not return BaseMessages placeholder: " + resolved);
    // Humanized leaf at minimum
    assertTrue(resolved.toLowerCase().contains("project"), resolved);
  }

  @Test
  void resolveI18nPlainTextUnchanged() {
    assertEquals(
        "Vault address",
        HGuiWidgetAdapter.resolveI18n("Vault address", FakeVaultResolver.class));
  }

  @Test
  void humanizeKeyUsesLeaf() {
    assertEquals(
        "Project Id",
        HGuiWidgetAdapter.humanizeKey("GooleSecretManagerVariableResolver.label.ProjectId"));
  }
}
