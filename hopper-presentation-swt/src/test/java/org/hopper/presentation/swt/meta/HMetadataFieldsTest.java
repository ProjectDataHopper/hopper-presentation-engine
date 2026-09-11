package org.hopper.presentation.swt.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.hopper.audit.HAuditSinkMeta;
import org.hopper.config.HServerSettings;
import org.hopper.config.HSystemVariables;
import org.hopper.core.HColorRGB;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.history.HUserHistory;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.pictorial.HPictorialSeries;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.security.HSecurityAcl;
import org.hopper.security.HSecurityRole;
import org.hopper.security.HSecurityUser;

class HMetadataFieldsTest {

  @Test
  void discoversDatabaseConnectionScalars() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HDatabaseConnection.class);
    assertTrue(specs.stream().anyMatch(s -> "hostname".equals(s.field().getName())));
    assertTrue(specs.stream().anyMatch(s -> s.password() && "password".equals(s.field().getName())));
    assertFalse(specs.stream().anyMatch(s -> "name".equals(s.field().getName())));
  }

  @Test
  void discoversPresentationPagesList() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HPresentation.class);
    HMetadataFields.Spec pages =
        specs.stream().filter(s -> "pages".equals(s.field().getName())).findFirst().orElseThrow();
    assertEquals(HPage.class, pages.listElementType());
  }

  @Test
  void infersThemeComboOnPresentation() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HPresentation.class);
    HMetadataFields.Spec theme =
        specs.stream()
            .filter(s -> "defaultThemeName".equals(s.field().getName()))
            .findFirst()
            .orElseThrow();
    assertEquals(HComboSource.THEMES, theme.comboSource());
    assertEquals(HWidgetType.COMBO, theme.widgetType());
  }

  @Test
  void infersLayoutModeAndDatabaseTypeCombos() {
    HMetadataFields.Spec layoutMode =
        HMetadataFields.discover(HPresentation.class).stream()
            .filter(s -> "layoutMode".equals(s.field().getName()))
            .findFirst()
            .orElseThrow();
    assertEquals(HWidgetType.COMBO, layoutMode.widgetType());
    HMetadataFields.Spec databaseType =
        HMetadataFields.discover(HDatabaseConnection.class).stream()
            .filter(s -> "databaseTypeCode".equals(s.field().getName()))
            .findFirst()
            .orElseThrow();
    assertEquals(HWidgetType.COMBO, databaseType.widgetType());
  }

  @Test
  void discoversConnectorPluginField() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HConnector.class);
    assertTrue(
        specs.stream()
            .anyMatch(
                s ->
                    "connector".equals(s.field().getName())
                        && HMetadataFields.isPluginInterface(s.type())));
  }

  @Test
  void nestedComponentKeepsName() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HComponent.class);
    assertTrue(specs.stream().anyMatch(s -> "name".equals(s.field().getName())));
    assertTrue(specs.stream().anyMatch(s -> "component".equals(s.field().getName())));
    assertTrue(specs.stream().anyMatch(s -> "layout".equals(s.field().getName())));
  }

  @Test
  void themeHasColorFields() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HTheme.class);
    assertTrue(specs.stream().anyMatch(s -> s.type() == HColorRGB.class));
    assertTrue(specs.stream().anyMatch(s -> s.type() == org.hopper.core.HFont.class));
  }

  @Test
  void pictorialSeriesIncludesImageMap() {
    List<HMetadataFields.Spec> specs = HMetadataFields.discover(HPictorialSeries.class);
    assertTrue(specs.stream().anyMatch(s -> "imageMap".equals(s.field().getName()) && s.isMap()));
  }

  @Test
  void humanizeSplitsCamelCase() {
    assertEquals("Default Theme Name", HMetadataFields.humanize("defaultThemeName"));
  }

  @Test
  void convertBoxedIntegerFromString() {
    assertEquals(12, HMetadataFields.convert(Integer.class, "12"));
    assertNull(HMetadataFields.convert(Integer.class, ""));
    assertNull(HMetadataFields.convert(Integer.class, "  "));
    assertEquals(0, HMetadataFields.convert(int.class, ""));
    assertEquals(7, HMetadataFields.convert(int.class, "7"));
  }

  @Test
  void convertEnumFromName() {
    assertEquals(
        org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode
            .STEP_IMAGES,
        HMetadataFields.convert(
            org.hopper.presentation.component.types.pictorial.HPictorialChartComponent.RenderMode
                .class,
            "STEP_IMAGES"));
  }

  @Test
  void newPageDefaultsToA4() {
    HPage page = (HPage) HMetadataFields.newInstance(HPage.class);
    assertTrue(page.getWidth() > 0);
    assertTrue(page.getHeight() > 0);
  }

  @Test
  void labelOfUsesPageSize() {
    HPage page = HPage.getA4(true);
    String label = HMetadataFields.labelOf(page, 0);
    assertTrue(label.contains(String.valueOf(page.getWidth())));
    assertFalse(label.contains("@"));
  }

  @Test
  void everyCatalogTypeHasEditorClass() {
    Class<?>[] types = {
      HPresentation.class,
      HConnector.class,
      HTheme.class,
      HDatabaseConnection.class,
      HPictorialSeries.class,
      HServerSettings.class,
      HSystemVariables.class,
      HSecurityUser.class,
      HSecurityRole.class,
      HSecurityAcl.class,
      HAuditSinkMeta.class,
      HUserHistory.class
    };
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (Class<?> type : types) {
      String path = type.getName().replace('.', '/') + "Editor.class";
      assertNotNull(loader.getResource(path), "Missing editor class " + path);
    }
  }
}
