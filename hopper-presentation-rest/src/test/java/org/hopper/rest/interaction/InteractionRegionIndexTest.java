/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.rest.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.hopper.core.HGeometry;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;

class InteractionRegionIndexTest {

  @Test
  void emptyWhenNoInteractions() {
    HPresentation p = new HPresentation();
    HRenderPage page = syntheticPage();
    page.getDrawnItems()
        .add(
            new DrawnItem(
                "t",
                "HTableComponent",
                0,
                DrawnItem.DrawnItemType.ComponentItem,
                "Cell",
                0,
                0,
                new HGeometry(0, 0, 20, 10),
                new DrawnContext("A")));

    Map<String, Object> body = InteractionRegionIndex.build(p, page);
    assertTrue(((List<?>) body.get("interactions")).isEmpty());
    assertTrue(((List<?>) body.get("regions")).isEmpty());
  }

  @Test
  void cellRegionsShareInteractionDefAndPreserveContext() {
    HPresentation p = new HPresentation();
    HInteractionLocation loc = new HInteractionLocation();
    loc.setComponentName("t");
    loc.setItemType("ComponentItem");
    loc.setItemCategory("Cell");
    p.getInteractions()
        .add(
            new HInteraction(
                HInteractionMethod.SINGLE_CLICK,
                loc,
                new HInteractionAction(
                    HInteractionAction.ActionType.OPEN_PRESENTATION, "Detail")));

    HRenderPage page = syntheticPage();
    // Envelope
    page.getDrawnItems()
        .add(
            new DrawnItem(
                "t",
                "HTableComponent",
                0,
                DrawnItem.DrawnItemType.Component,
                DrawnItem.Category.ComponentArea.name(),
                0,
                0,
                new HGeometry(0, 0, 100, 50)));
    // Two cells
    page.getDrawnItems()
        .add(
            new DrawnItem(
                "t",
                "HTableComponent",
                0,
                DrawnItem.DrawnItemType.ComponentItem,
                "Cell",
                0,
                0,
                new HGeometry(0, 0, 50, 25),
                new DrawnContext("Acme")));
    page.getDrawnItems()
        .add(
            new DrawnItem(
                "t",
                "HTableComponent",
                0,
                DrawnItem.DrawnItemType.ComponentItem,
                "Cell",
                1,
                0,
                new HGeometry(0, 25, 50, 25),
                new DrawnContext("Beta")));

    Map<String, Object> body = InteractionRegionIndex.build(p, page);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> interactions = (List<Map<String, Object>>) body.get("interactions");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> regions = (List<Map<String, Object>>) body.get("regions");

    assertEquals(1, interactions.size());
    assertEquals("SINGLE_CLICK", interactions.get(0).get("method"));
    assertEquals(2, regions.size());

    @SuppressWarnings("unchecked")
    Map<String, Object> hit0 = (Map<String, Object>) regions.get(0).get("hit");
    assertEquals(50, hit0.get("width"));
    @SuppressWarnings("unchecked")
    Map<String, Object> drawn0 = (Map<String, Object>) regions.get(0).get("drawnItem");
    @SuppressWarnings("unchecked")
    Map<String, Object> ctx0 = (Map<String, Object>) drawn0.get("context");
    assertEquals("Acme", ctx0.get("value"));

    // Point lookup agrees with first cell
    InteractionLookupResult at = InteractionRegionIndex.lookupAt(p, page, 10, 5, null);
    assertTrue(at.isFound());
    assertNotNull(at.getDrawnItem());
    assertEquals("Acme", at.getDrawnItem().getContext().getValue());
    assertEquals(HInteractionMethod.SINGLE_CLICK, at.getMethod());

    // Miss
    InteractionLookupResult miss = InteractionRegionIndex.lookupAt(p, page, 200, 200, null);
    assertFalse(miss.isFound());
  }

  @Test
  void wholeComponentOnlyCollapsesToSingleEnvelopeRegion() {
    HPresentation p = new HPresentation();
    HInteractionLocation loc = new HInteractionLocation();
    loc.setComponentName("chart");
    loc.setItemType("Component");
    loc.setItemCategory("ComponentArea");
    p.getInteractions()
        .add(
            new HInteraction(
                HInteractionMethod.MOUSE_HOVER,
                loc,
                new HInteractionAction(HInteractionAction.ActionType.POPUP_CONTEXT_INFORMATION)));

    HRenderPage page = syntheticPage();
    page.getDrawnItems()
        .add(
            new DrawnItem(
                "chart",
                "HBarChartComponent",
                0,
                DrawnItem.DrawnItemType.Component,
                DrawnItem.Category.ComponentArea.name(),
                0,
                0,
                new HGeometry(10, 10, 200, 100)));
    // Child items would explode region count without collapse
    for (int i = 0; i < 5; i++) {
      page.getDrawnItems()
          .add(
              new DrawnItem(
                  "chart",
                  "HBarChartComponent",
                  0,
                  DrawnItem.DrawnItemType.ComponentItem,
                  "ChartLabel",
                  i,
                  0,
                  new HGeometry(20 + i * 30, 40, 25, 40),
                  new DrawnContext("v" + i)));
    }

    Map<String, Object> body = InteractionRegionIndex.build(p, page);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> regions = (List<Map<String, Object>>) body.get("regions");
    assertEquals(1, regions.size(), "whole-component-only should collapse to one envelope region");
    @SuppressWarnings("unchecked")
    Map<String, Object> hit = (Map<String, Object>) regions.get(0).get("hit");
    assertEquals(10, hit.get("x"));
    assertEquals(200, hit.get("width"));

    InteractionLookupResult at = InteractionRegionIndex.lookupAt(p, page, 50, 50, null);
    assertTrue(at.isFound());
    assertEquals(HInteractionMethod.MOUSE_HOVER, at.getMethod());
  }

  @Test
  void hoverAndClickBothListedInSharedDefs() {
    HPresentation p = new HPresentation();
    HInteractionLocation loc = new HInteractionLocation();
    loc.setComponentName("t");
    loc.setItemType("ComponentItem");
    loc.setItemCategory("Cell");
    p.getInteractions()
        .add(
            new HInteraction(
                HInteractionMethod.MOUSE_HOVER,
                loc,
                new HInteractionAction(HInteractionAction.ActionType.POPUP_CONTEXT_INFORMATION)));
    p.getInteractions()
        .add(
            new HInteraction(
                HInteractionMethod.SINGLE_CLICK,
                loc,
                new HInteractionAction(
                    HInteractionAction.ActionType.OPEN_PRESENTATION, "Detail")));

    HRenderPage page = syntheticPage();
    page.getDrawnItems()
        .add(
            new DrawnItem(
                "t",
                "HTableComponent",
                0,
                DrawnItem.DrawnItemType.ComponentItem,
                "Cell",
                0,
                0,
                new HGeometry(0, 0, 40, 20),
                new DrawnContext("X")));

    Map<String, Object> body = InteractionRegionIndex.build(p, page);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> interactions = (List<Map<String, Object>>) body.get("interactions");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> regions = (List<Map<String, Object>>) body.get("regions");
    assertEquals(2, interactions.size());
    assertEquals(1, regions.size());
    @SuppressWarnings("unchecked")
    List<Integer> ids = (List<Integer>) regions.get(0).get("interactionIds");
    assertEquals(2, ids.size());
  }

  private static HRenderPage syntheticPage() {
    HPage pageMeta = new HPage();
    pageMeta.setWidth(400);
    pageMeta.setHeight(300);
    HRenderPage page = new HRenderPage(pageMeta);
    page.setPageNumber(1);
    return page;
  }
}
