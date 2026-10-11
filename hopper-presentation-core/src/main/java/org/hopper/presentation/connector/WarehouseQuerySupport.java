package org.hopper.presentation.connector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.types.chain.HChainConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;
import org.hopper.presentation.page.HPage;

/**
 * Whether opening a presentation will run a warehouse SQL query. In-memory charts (Gantt,
 * performance) do not, so the UI can skip the progress dialog that would only flash.
 */
public final class WarehouseQuerySupport {

  private WarehouseQuerySupport() {}

  /**
   * {@code true} when the presentation is unknown or any referenced connector reads SQL. A
   * presentation with no connectors, or only in-memory ones, returns {@code false}.
   */
  public static boolean needsProgressDialog(
      HPresentation presentation, IHopMetadataProvider metadataProvider) {
    if (presentation == null) {
      return true;
    }
    Set<String> connectorNames = new HashSet<>();
    collect(presentation.getHeader(), connectorNames);
    collect(presentation.getFooter(), connectorNames);
    if (presentation.getPages() != null) {
      for (HPage page : presentation.getPages()) {
        collect(page, connectorNames);
      }
    }
    if (connectorNames.isEmpty()) {
      return false;
    }
    if (metadataProvider == null) {
      return true;
    }
    try {
      IHopMetadataSerializer<HConnector> serializer =
          metadataProvider.getSerializer(HConnector.class);
      Set<String> seen = new HashSet<>();
      for (String name : connectorNames) {
        if (namedConnectorNeedsProgress(name, serializer, seen)) {
          return true;
        }
      }
    } catch (HopException e) {
      return true;
    }
    return false;
  }

  private static void collect(HPage page, Set<String> connectorNames) {
    if (page == null || page.getComponents() == null) {
      return;
    }
    for (HComponent component : page.getComponents()) {
      collect(component, connectorNames);
    }
  }

  private static void collect(HComponent component, Set<String> connectorNames) {
    if (component == null) {
      return;
    }
    collect(component.getComponent(), connectorNames);
  }

  private static void collect(IHComponent component, Set<String> connectorNames) {
    if (component instanceof HBaseComponent base
        && StringUtils.isNotBlank(base.getSourceConnectorName())) {
      connectorNames.add(base.getSourceConnectorName().trim());
    }
    if (component instanceof HGroupComponent group) {
      collect(group.getGroupComponent(), connectorNames);
    }
  }

  private static boolean namedConnectorNeedsProgress(
      String name, IHopMetadataSerializer<HConnector> serializer, Set<String> seen)
      throws HopException {
    if (StringUtils.isBlank(name) || !seen.add(name)) {
      return false;
    }
    HConnector connector = serializer.load(name);
    if (connector == null) {
      return true;
    }
    return connectorNeedsProgress(connector.getConnector(), serializer, seen);
  }

  private static boolean connectorNeedsProgress(
      IHConnector connector, IHopMetadataSerializer<HConnector> serializer, Set<String> seen)
      throws HopException {
    if (connector instanceof HSqlConnector) {
      return true;
    }
    if (connector instanceof HBaseConnector base
        && namedConnectorNeedsProgress(base.getSourceConnectorName(), serializer, seen)) {
      return true;
    }
    if (connector instanceof HChainConnector chain) {
      List<IHConnector> nested = chain.getConnectors();
      if (nested != null) {
        for (IHConnector step : nested) {
          if (connectorNeedsProgress(step, serializer, seen)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
