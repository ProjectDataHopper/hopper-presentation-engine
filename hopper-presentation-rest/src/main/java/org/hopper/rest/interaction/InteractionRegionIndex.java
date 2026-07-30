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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hopper.core.HGeometry;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HRenderPage;

/**
 * Builds a page-scoped interaction region index for client-side hover/click hit-testing, and
 * shares point-lookup logic with {@code POST /render/lookupActions/}.
 *
 * <p>Payload keeps shared interaction definitions and per-region geometry + hit context so the
 * browser can highlight and resolve actions without per-mousemove round-trips.
 */
public final class InteractionRegionIndex {

  private InteractionRegionIndex() {}

  /**
   * Compact JSON-ready map: {@code interactions[]} + {@code regions[]} (+ optional metadata
   * fields filled by the caller).
   */
  public static Map<String, Object> build(HPresentation presentation, HRenderPage page) {
    Map<String, Object> body = new LinkedHashMap<>();
    List<Map<String, Object>> interactionsOut = new ArrayList<>();
    List<Map<String, Object>> regionsOut = new ArrayList<>();
    body.put("interactions", interactionsOut);
    body.put("regions", regionsOut);

    if (presentation == null
        || page == null
        || page.getDrawnItems() == null
        || presentation.getInteractions() == null
        || presentation.getInteractions().isEmpty()) {
      return body;
    }

    Set<String> componentsWithSpecificLocations =
        collectComponentsWithSpecificLocations(presentation.getInteractions());

    // identity → compact id assigned when first used in a region
    Map<HInteraction, Integer> interactionIds = new IdentityHashMap<>();
    Set<String> wholeComponentEnvelopeEmitted = new LinkedHashSet<>();

    List<DrawnItem> drawnItems = page.getDrawnItems();
    for (int z = 0; z < drawnItems.size(); z++) {
      DrawnItem drawnItem = drawnItems.get(z);
      if (drawnItem == null || drawnItem.getGeometry() == null) {
        continue;
      }
      HGeometry hitGeo = drawnItem.getGeometry();
      if (hitGeo.getWidth() <= 0 && hitGeo.getHeight() <= 0) {
        continue;
      }

      List<HInteraction> matches = presentation.findInteractions(null, drawnItem);
      if (matches.isEmpty()) {
        continue;
      }

      String componentName = drawnItem.getComponentName();
      boolean onlyWholeComponent = onlyWholeComponentMatches(matches);
      boolean collapseWholeComponent =
          onlyWholeComponent
              && componentName != null
              && !componentsWithSpecificLocations.contains(componentName);

      if (collapseWholeComponent) {
        if (wholeComponentEnvelopeEmitted.contains(componentName)) {
          continue;
        }
        DrawnItem envelope = page.lookupComponentDrawnItem(componentName);
        DrawnItem outlineItem = envelope != null ? envelope : drawnItem;
        HGeometry outlineGeo =
            outlineItem.getGeometry() != null ? outlineItem.getGeometry() : hitGeo;
        // Prefer envelope for both hit and outline so empty padding is active
        HGeometry hit = outlineGeo;
        if (hit.getWidth() <= 0 && hit.getHeight() <= 0) {
          continue;
        }
        regionsOut.add(
            buildRegion(
                z,
                hit,
                outlineGeo,
                outlineItem,
                matches,
                interactionIds,
                interactionsOut));
        wholeComponentEnvelopeEmitted.add(componentName);
        continue;
      }

      DrawnItem outlineItem = resolveOutlineItem(page, drawnItem, matches);
      HGeometry outlineGeo =
          outlineItem.getGeometry() != null ? outlineItem.getGeometry() : hitGeo;
      regionsOut.add(
          buildRegion(z, hitGeo, outlineGeo, drawnItem, matches, interactionIds, interactionsOut));
    }

    return body;
  }

  /**
   * Point hit-test matching historical {@code lookupActions} behaviour: top-most drawn item under
   * (x,y), plus component envelopes as candidates; first interaction match wins.
   */
  public static InteractionLookupResult lookupAt(
      HPresentation presentation,
      HRenderPage page,
      int x,
      int y,
      HInteractionMethod methodFilter) {
    InteractionLookupResult result = new InteractionLookupResult();
    if (presentation == null || page == null) {
      return result;
    }

    List<DrawnItem> hits = page.lookupDrawnItems(x, y);
    LinkedHashSet<String> componentNames = new LinkedHashSet<>();
    for (DrawnItem hit : hits) {
      if (hit.getComponentName() != null) {
        componentNames.add(hit.getComponentName());
      }
    }
    List<DrawnItem> candidates = new ArrayList<>(hits);
    for (String name : componentNames) {
      DrawnItem envelope = page.lookupComponentDrawnItem(name);
      if (envelope != null && !candidates.contains(envelope)) {
        candidates.add(envelope);
      }
    }

    for (DrawnItem drawnItem : candidates) {
      List<HInteraction> interactions = presentation.findInteractions(methodFilter, drawnItem);
      if (interactions.isEmpty()) {
        continue;
      }

      DrawnItem outlineItem = resolveOutlineItem(page, drawnItem, interactions);

      result.setFound(true);
      result.setDrawnItem(outlineItem);
      List<InteractionLookupResult.InteractionMatch> matchList = new ArrayList<>();
      HInteraction primary = null;
      for (HInteraction interaction : interactions) {
        HInteractionMethod m =
            interaction.getMethod() != null
                ? interaction.getMethod()
                : HInteractionMethod.SINGLE_CLICK;
        List<HInteractionAction> acts =
            interaction.getActions() != null
                ? interaction.getActions()
                : Collections.emptyList();
        matchList.add(new InteractionLookupResult.InteractionMatch(m, acts));
        if (primary == null) {
          primary = interaction;
        }
      }
      // Prefer first click match for top-level method/actions (click path)
      for (HInteraction interaction : interactions) {
        HInteractionMethod m =
            interaction.getMethod() != null
                ? interaction.getMethod()
                : HInteractionMethod.SINGLE_CLICK;
        if (m.isClick()) {
          primary = interaction;
          break;
        }
      }
      if (primary == null) {
        primary = interactions.get(0);
      }
      result.setMatches(matchList);
      result.setMethod(
          primary.getMethod() != null ? primary.getMethod() : HInteractionMethod.SINGLE_CLICK);
      result.setActions(
          primary.getActions() != null ? primary.getActions() : Collections.emptyList());
      break;
    }
    return result;
  }

  private static DrawnItem resolveOutlineItem(
      HRenderPage page, DrawnItem drawnItem, List<HInteraction> interactions) {
    DrawnItem outlineItem = drawnItem;
    for (HInteraction interaction : interactions) {
      if (isWholeComponent(interaction)
          && drawnItem.getComponentName() != null) {
        DrawnItem envelope = page.lookupComponentDrawnItem(drawnItem.getComponentName());
        if (envelope != null) {
          outlineItem = envelope;
        }
        break;
      }
    }
    return outlineItem;
  }

  private static boolean isWholeComponent(HInteraction interaction) {
    if (interaction == null || interaction.getLocation() == null) {
      return false;
    }
    return DrawnItem.DrawnItemType.Component.name()
        .equals(interaction.getLocation().getItemType());
  }

  private static boolean onlyWholeComponentMatches(List<HInteraction> matches) {
    if (matches == null || matches.isEmpty()) {
      return false;
    }
    for (HInteraction i : matches) {
      if (!isWholeComponent(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Component names that have at least one non-whole-component interaction location (cell, chart
   * label, …). Those need per-item regions so hit context stays correct.
   */
  private static Set<String> collectComponentsWithSpecificLocations(List<HInteraction> interactions) {
    Set<String> names = new LinkedHashSet<>();
    for (HInteraction interaction : interactions) {
      if (interaction == null || interaction.getLocation() == null) {
        continue;
      }
      if (isWholeComponent(interaction)) {
        continue;
      }
      HInteractionLocation loc = interaction.getLocation();
      if (loc.getComponentName() != null && !loc.getComponentName().isBlank()) {
        names.add(loc.getComponentName());
      }
    }
    return names;
  }

  private static Map<String, Object> buildRegion(
      int z,
      HGeometry hit,
      HGeometry outline,
      DrawnItem sourceItem,
      List<HInteraction> matches,
      Map<HInteraction, Integer> interactionIds,
      List<Map<String, Object>> interactionsOut) {
    Map<String, Object> region = new LinkedHashMap<>();
    region.put("z", z);
    region.put("hit", geometryMap(hit));
    region.put("outline", geometryMap(outline));
    region.put("drawnItem", drawnItemMap(sourceItem));

    List<Integer> ids = new ArrayList<>();
    for (HInteraction interaction : matches) {
      Integer id = interactionIds.get(interaction);
      if (id == null) {
        id = interactionsOut.size();
        interactionIds.put(interaction, id);
        interactionsOut.add(interactionDefMap(id, interaction));
      }
      ids.add(id);
    }
    region.put("interactionIds", ids);
    return region;
  }

  private static Map<String, Object> interactionDefMap(int id, HInteraction interaction) {
    Map<String, Object> def = new LinkedHashMap<>();
    def.put("id", id);
    HInteractionMethod m =
        interaction.getMethod() != null
            ? interaction.getMethod()
            : HInteractionMethod.SINGLE_CLICK;
    def.put("method", m.name());
    List<HInteractionAction> acts =
        interaction.getActions() != null ? interaction.getActions() : Collections.emptyList();
    def.put("actions", acts);
    return def;
  }

  private static Map<String, Object> geometryMap(HGeometry geo) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (geo == null) {
      m.put("x", 0);
      m.put("y", 0);
      m.put("width", 0);
      m.put("height", 0);
      return m;
    }
    m.put("x", geo.getX());
    m.put("y", geo.getY());
    m.put("width", Math.max(0, geo.getWidth()));
    m.put("height", Math.max(0, geo.getHeight()));
    return m;
  }

  private static Map<String, Object> drawnItemMap(DrawnItem item) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (item == null) {
      return m;
    }
    m.put("componentName", item.getComponentName());
    m.put("componentPluginId", item.getComponentPluginId());
    if (item.getType() != null) {
      m.put("type", item.getType().name());
    }
    m.put("category", item.getCategory());
    m.put("rowNr", item.getRowNr());
    m.put("colNr", item.getColNr());
    m.put("partNumber", item.getPartNumber());
    DrawnContext ctx = item.getContext();
    if (ctx != null) {
      Map<String, Object> cm = new LinkedHashMap<>();
      if (ctx.getValue() != null) {
        cm.put("value", ctx.getValue());
      }
      if (ctx.getDimensionValues() != null && !ctx.getDimensionValues().isEmpty()) {
        cm.put("dimensionValues", new LinkedHashMap<>(ctx.getDimensionValues()));
      }
      // Minimal dimension column names (for valueParameter fallbacks in JS)
      if (ctx.getDimensions() != null && !ctx.getDimensions().isEmpty()) {
        List<Map<String, String>> dims = new ArrayList<>();
        for (var col : ctx.getDimensions()) {
          if (col == null || col.getColumnName() == null) {
            continue;
          }
          Map<String, String> d = new LinkedHashMap<>();
          d.put("columnName", col.getColumnName());
          dims.add(d);
        }
        if (!dims.isEmpty()) {
          cm.put("dimensions", dims);
        }
      }
      if (!cm.isEmpty()) {
        m.put("context", cm);
      }
    }
    return m;
  }
}
