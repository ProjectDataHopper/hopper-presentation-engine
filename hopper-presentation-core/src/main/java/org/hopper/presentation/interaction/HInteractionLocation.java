package org.hopper.presentation.interaction;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HColumn;
import org.hopper.core.draw.DrawnItem;

/** Describe where an interaction can take place */
@Getter
@Setter
public class HInteractionLocation {

  @HopMetadataProperty private String componentName;
  @HopMetadataProperty private String componentPluginId;
  @HopMetadataProperty private String itemType;
  @HopMetadataProperty private String itemCategory;
  @HopMetadataProperty private List<String> dimensionColumns;

  public HInteractionLocation() {
    this.dimensionColumns = new ArrayList<>();
  }

  public HInteractionLocation(
      String componentName,
      String componentPluginId,
      String itemType,
      String itemCategory,
      List<String> dimensions) {
    this.componentName = componentName;
    this.componentPluginId = componentPluginId;
    this.itemType = itemType;
    this.itemCategory = itemCategory;
    this.dimensionColumns = dimensions;
  }

  public HInteractionLocation(HInteractionLocation location) {
    this();
    this.componentName = location.componentName;
    this.componentPluginId = location.componentPluginId;
    this.itemType = location.itemType;
    this.itemCategory = location.itemCategory;
    this.dimensionColumns.addAll(location.dimensionColumns);
  }

  public boolean matches(DrawnItem drawnItem) {
    if (drawnItem == null) {
      return false;
    }
    if (StringUtils.isNotEmpty(componentName)
        && !drawnItem.getComponentName().equals(componentName)) {
      return false;
    }

    // Whole-component interactions: any drawn item that belongs to the component counts
    // (Component area, series labels, cells, …). Hit-testing usually returns the top-most
    // ComponentItem, not the Component envelope — still match so the whole area is active.
    boolean wholeComponent =
        StringUtils.isNotEmpty(itemType)
            && DrawnItem.DrawnItemType.Component.name().equals(itemType);

    if (!wholeComponent
        && StringUtils.isNotEmpty(componentPluginId)
        && StringUtils.isNotEmpty(drawnItem.getComponentPluginId())
        && !drawnItem.getComponentPluginId().equals(componentPluginId)) {
      return false;
    }
    // Soft plugin check for whole-component: only enforce when the configured plugin id
    // looks like a real plugin id (e.g. HLineChartComponent), not a mistaken component name.
    if (wholeComponent
        && StringUtils.isNotEmpty(componentPluginId)
        && StringUtils.isNotEmpty(drawnItem.getComponentPluginId())
        && looksLikePluginId(componentPluginId)
        && !drawnItem.getComponentPluginId().equals(componentPluginId)) {
      return false;
    }

    if (StringUtils.isNotEmpty(itemType)) {
      if (wholeComponent) {
        // Accept Component or any ComponentItem of this component (name already checked).
      } else if (!drawnItem.getType().name().equals(itemType)) {
        return false;
      }
    }
    if (!wholeComponent
        && StringUtils.isNotEmpty(itemCategory)
        && (drawnItem.getCategory() == null
            || !drawnItem.getCategory().equals(itemCategory))) {
      return false;
    }
    if (dimensionColumns != null && !dimensionColumns.isEmpty()) {
      if (drawnItem.getContext() == null || drawnItem.getContext().getDimensions() == null) {
        return false;
      }
      for (String dimensionName : dimensionColumns) {
        boolean found = false;
        for (HColumn dimension : drawnItem.getContext().getDimensions()) {
          if (dimension != null
              && dimension.getColumnName() != null
              && dimension.getColumnName().equals(dimensionName)) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
    }
    return true;
  }

  /** True for registry-style ids: legacy {@code Lean*} or current {@code H*} (e.g. HTableComponent). */
  private static boolean looksLikePluginId(String id) {
    if (id == null || id.length() < 2) {
      return false;
    }
    if (id.startsWith("Lean") && id.length() > 4 && Character.isUpperCase(id.charAt(4))) {
      return true;
    }
    return id.charAt(0) == 'H' && Character.isUpperCase(id.charAt(1));
  }
}
