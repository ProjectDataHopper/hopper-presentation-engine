package org.hopper.presentation.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.HGeometry;

class HInteractionMethodTest {

  @Test
  void fromStringCodes() {
    assertEquals(HInteractionMethod.SINGLE_CLICK, HInteractionMethod.fromString("SINGLE_CLICK"));
    assertEquals(HInteractionMethod.DOUBLE_CLICK, HInteractionMethod.fromString("double_click"));
    assertEquals(HInteractionMethod.MOUSE_HOVER, HInteractionMethod.fromString("hover"));
  }

  @Test
  void hoverAndClickCanCoexistOnSameLocation() {
    DrawnItem item =
        new DrawnItem(
            "t",
            "HTableComponent",
            1,
            DrawnItem.DrawnItemType.ComponentItem,
            "Cell",
            0,
            0,
            new HGeometry(0, 0, 10, 10),
            new DrawnContext("EMEA"));

    HInteractionLocation loc = new HInteractionLocation();
    loc.setComponentName("t");
    loc.setItemType("ComponentItem");
    loc.setItemCategory("Cell");

    HInteraction hover =
        new HInteraction(
            HInteractionMethod.MOUSE_HOVER,
            loc,
            new HInteractionAction(HInteractionAction.ActionType.POPUP_CONTEXT_INFORMATION));
    HInteraction click =
        new HInteraction(
            HInteractionMethod.SINGLE_CLICK,
            loc,
            new HInteractionAction(
                HInteractionAction.ActionType.OPEN_PRESENTATION, "Detail"));

    org.hopper.presentation.HPresentation p = new org.hopper.presentation.HPresentation();
    p.getInteractions().add(hover);
    p.getInteractions().add(click);

    List<HInteraction> all = p.findInteractions(null, item);
    assertEquals(2, all.size());
    assertEquals(1, p.findInteractions(HInteractionMethod.MOUSE_HOVER, item).size());
    assertEquals(1, p.findInteractions(HInteractionMethod.SINGLE_CLICK, item).size());
    assertTrue(p.findInteraction(HInteractionMethod.SINGLE_CLICK, item).getMethod().isClick());
    assertFalse(p.findInteraction(HInteractionMethod.MOUSE_HOVER, item).getMethod().isClick());
  }

  @Test
  void actionTypePopupFlags() {
    assertTrue(HInteractionAction.ActionType.POPUP_CONTEXT_INFORMATION.isPopup());
    assertTrue(HInteractionAction.ActionType.POPUP_PRESENTATION.isPopup());
    assertFalse(HInteractionAction.ActionType.OPEN_PRESENTATION.isPopup());
  }
}
