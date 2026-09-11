package org.hopper.presentation.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HAttachment;
import org.hopper.core.HEnvironment;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;


class HPresentationSessionTest {

  private IHopMetadataProvider metadataProvider;
  private LoggingObject log;

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    metadataProvider = new MemoryMetadataProvider();
    log = new LoggingObject("session-test");
    HTheme theme = HTheme.getDefault();
    metadataProvider.getSerializer(HTheme.class).save(theme);
  }

  @Test
  void openRendersSvgAndPageSwitchDoesNotRelayout() throws Exception {
    saveDashboardAndDetail();
    HPresentationSession session = new HPresentationSession(log, metadataProvider);
    session.open("Dashboard");
    assertEquals("Dashboard", session.getCurrentPresentationName());
    assertEquals(1, session.pageCount());
    assertNotNull(session.svgXml());
    assertTrue(session.svgXml().contains("svg") || session.svgXml().contains("SVG"));

    HPresentation first = session.getCurrentPresentation();
    session.setPage(0);
    assertEquals(first, session.getCurrentPresentation());
  }

  @Test
  void clickOpensTargetPresentationWithParameter() throws Exception {
    saveDashboardAndDetail();
    HPresentationSession session = new HPresentationSession(log, metadataProvider);
    session.open("Dashboard");

    DrawnItem item = findLabelItem(session, "Detail");
    assertNotNull(item, "expected a drawn item for the Detail label");
    int x = item.getGeometry().getX() + Math.max(1, item.getGeometry().getWidth() / 2);
    int y = item.getGeometry().getY() + Math.max(1, item.getGeometry().getHeight() / 2);

    List<HHostCommand> commands =
        session.applyPointer(HInteractionMethod.SINGLE_CLICK, x, y, true);
    assertFalse(commands.isEmpty());
    assertEquals(HHostCommand.Type.OPEN_PRESENTATION, commands.get(0).getType());
    assertEquals("Detail", commands.get(0).getObjectName());
    assertEquals("Detail", session.getCurrentPresentationName());
  }

  @Test
  void goHomeReturnsToDashboard() throws Exception {
    saveDashboardAndDetail();
    HPresentationSession session = new HPresentationSession(log, metadataProvider);
    session.open("Dashboard");
    session.open("Detail", new ArrayList<>(), false);
    assertEquals("Detail", session.getCurrentPresentationName());
    session.goHome();
    assertEquals("Dashboard", session.getCurrentPresentationName());
  }

  private DrawnItem findLabelItem(HPresentationSession session, String name) {
    for (DrawnItem item : session.currentPage().getDrawnItems()) {
      if (name.equals(item.getComponentName())) {
        return item;
      }
    }
    return null;
  }

  private void saveDashboardAndDetail() throws Exception {
    metadataProvider.getSerializer(HPresentation.class).save(labelPresentation("Detail", "Dashboard"));
    metadataProvider.getSerializer(HPresentation.class).save(dashboard("Dashboard", List.of("Detail")));
  }

  private static HInteractionLocation location(String componentName) {
    return new HInteractionLocation(
        componentName, "HLabelComponent", null, null, Collections.emptyList());
  }

  private static HPresentation dashboard(String name, List<String> targets) {
    HPresentation presentation = new HPresentation();
    presentation.setName(name);
    HPage page = HPage.getA4(true);
    presentation.getPages().add(page);
    String relative = null;
    for (String target : targets) {
      HLabelComponent label = new HLabelComponent();
      label.setLabel(target);
      label.setHorizontalAlignment(HHorizontalAlignment.LEFT);
      label.setVerticalAlignment(HVerticalAlignment.BOTTOM);
      HComponent component = new HComponent(target, label);
      HLayout layout = new HLayout();
      layout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
      if (relative == null) {
        layout.setTop(new HAttachment(null, 0, 100, HAttachment.Alignment.TOP));
      } else {
        layout.setTop(new HAttachment(relative, 0, 30, HAttachment.Alignment.BOTTOM));
      }
      component.setLayout(layout);
      HInteractionAction action =
          new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, target);
      action.setValueParameter("ITEM");
      presentation
          .getInteractions()
          .add(new HInteraction(HInteractionMethod.SINGLE_CLICK, location(target), action));
      page.getComponents().add(component);
      relative = target;
    }
    HTheme theme = HTheme.getDefault();
    presentation.setDefaultThemeName(theme.getName());
    return presentation;
  }

  private static HPresentation labelPresentation(String name, String home) {
    HPresentation presentation = new HPresentation();
    presentation.setName(name);
    HPage page = HPage.getA4(true);
    presentation.getPages().add(page);
    HLabelComponent label = new HLabelComponent();
    label.setLabel(name);
    HComponent component = new HComponent("Label", label);
    HLayout layout = new HLayout();
    layout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
    layout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
    component.setLayout(layout);
    page.getComponents().add(component);

    HLabelComponent back = new HLabelComponent();
    back.setLabel("Back to " + home);
    back.setHorizontalAlignment(HHorizontalAlignment.CENTER);
    back.setVerticalAlignment(HVerticalAlignment.MIDDLE);
    HComponent dash = new HComponent(home, back);
    HLayout dashLayout = new HLayout();
    dashLayout.setRight(new HAttachment(null, 0, 0, HAttachment.Alignment.RIGHT));
    dashLayout.setBottom(new HAttachment(null, 0, 0));
    dash.setLayout(dashLayout);
    page.getComponents().add(dash);
    presentation
        .getInteractions()
        .add(
            new HInteraction(
                HInteractionMethod.SINGLE_CLICK,
                location(home),
                new HInteractionAction(HInteractionAction.ActionType.OPEN_PRESENTATION, home)));
    presentation.setDefaultThemeName(HTheme.getDefault().getName());
    return presentation;
  }
}
