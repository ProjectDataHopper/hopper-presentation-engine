package org.hopper.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.hopper.core.HColorRGB;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;

public class TestUtil {

  public static void assertEqualPresentations(
      HPresentation presentation, HPresentation verify) {
    assertEquals(presentation.getDescription(), verify.getDescription());
    assertEquals(presentation.getPages().size(), verify.getPages().size());
    for (int p = 0; p < presentation.getPages().size(); p++) {
      HPage originPage = presentation.getPages().get(p);
      HPage verifyPage = verify.getPages().get(p);
      assertEqualsPages(originPage, verifyPage);
    }
    assertEquals(presentation.getDefaultThemeName(), verify.getDefaultThemeName());
  }

  public static void assertEqualConnectors(
      HConnector originConnector, HConnector verifyConnector) {
    assertEquals(originConnector.getName(), verifyConnector.getName());
    IHConnector originIConnector = originConnector.getConnector();
    IHConnector verifyIConnector = verifyConnector.getConnector();
    assertEquals(originIConnector.getPluginId(), verifyIConnector.getPluginId());
    assertEquals(
        originIConnector.getSourceConnectorName(), verifyIConnector.getSourceConnectorName());
  }

  public static void assertEqualsPages(HPage originPage, HPage verifyPage) {
    assertEquals(originPage.getComponents().size(), verifyPage.getComponents().size());

    for (int c = 0; c < originPage.getComponents().size(); c++) {
      HComponent originComponent = originPage.getComponents().get(c);
      HComponent verifyComponent = verifyPage.getComponents().get(c);

      assertEqualsComponents(originComponent, verifyComponent);
    }
  }

  public static void assertEqualsComponents(
      HComponent originComponent, HComponent verifyComponent) {
    IHComponent originIComponent = originComponent.getComponent();
    IHComponent verifyIComponent = verifyComponent.getComponent();

    assertEquals(originComponent.getName(), verifyComponent.getName());
    Assertions.assertEquals(originComponent.getClipSize(), verifyComponent.getClipSize());
    assertEquals(originIComponent.getPluginId(), verifyIComponent.getPluginId());
    assertEquals(
        originIComponent.getSourceConnectorName(), verifyIComponent.getSourceConnectorName());
    assertEquals(originIComponent.getDefaultColor(), verifyIComponent.getDefaultColor());
    assertEquals(originIComponent.getBackGroundColor(), verifyIComponent.getBackGroundColor());
    assertEquals(originIComponent.getBorderColor(), verifyIComponent.getBorderColor());
    Assertions.assertEquals(originIComponent.getDefaultFont(), verifyIComponent.getDefaultFont());
  }

  public static void assertEqualThemes(HTheme originTheme, HTheme verifyTheme) {
    assertEquals(originTheme.getName(), verifyTheme.getName());

    assertEquals(originTheme.getColors().size(), verifyTheme.getColors().size());
    for (int c = 0; c < originTheme.getColors().size(); c++) {
      HColorRGB originColor = originTheme.getColors().get(c);
      HColorRGB verifyColor = verifyTheme.getColors().get(c);
      assertEquals(originColor, verifyColor);
    }

    assertEquals(originTheme.getDefaultColor(), verifyTheme.getDefaultColor());
    Assertions.assertEquals(originTheme.getDefaultFont(), verifyTheme.getDefaultFont());
    assertEquals(originTheme.getBackgroundColor(), verifyTheme.getBackgroundColor());
    assertEquals(originTheme.getBorderColor(), verifyTheme.getBorderColor());
  }
}
