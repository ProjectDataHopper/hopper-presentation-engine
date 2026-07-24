package org.hopper.audit.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.HAuditOutcome;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.variable.HParameter;
import org.hopper.security.HAction;
import org.hopper.security.HPrincipal;
import org.hopper.security.HResourceRef;
import org.hopper.security.HResourceType;
import org.hopper.security.HSecurityContext;

/** Builds usage audit events from presentation metadata and an {@link HExecutionTrace}. */
public final class HUsageAudit {

  private HUsageAudit() {}

  public static HAuditEvent presentationRender(
      HPresentation presentation,
      List<HParameter> parameters,
      HExecutionTrace trace,
      String renderId) {
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.PRESENTATION_RENDER)
            .actor(HSecurityContext.requirePrincipalOrAnonymous())
            .actionCode(HAction.PRESENTATION_RENDER.code())
            .resource(
                presentation != null
                    ? HResourceRef.presentation(presentation.getName())
                    : null);
    event.setRequestId(HSecurityContext.getRequestId());
    event.setRenderId(renderId);
    if (trace != null) {
      event.setDurationMs(trace.getDurationMs());
      event.setOutcome(trace.getOutcome());
      event.setErrorMessage(trace.getErrorMessage());
      event.setExecution(trace.toExecutionMap());
    }
    event.setDesign(buildDesignMap(presentation, parameters));
    return event;
  }

  public static HAuditEvent connectorPreview(
      HConnector connector, HExecutionTrace trace, boolean ok, String errorMessage) {
    String name = connector != null ? connector.getName() : null;
    HAuditEvent event =
        HAuditEvent.of(HAuditEventType.CONNECTOR_PREVIEW)
            .actor(HSecurityContext.requirePrincipalOrAnonymous())
            .actionCode(HAction.CONNECTOR_PREVIEW.code())
            .resource(name != null ? HResourceRef.connector(name) : null);
    event.setRequestId(HSecurityContext.getRequestId());
    if (trace != null) {
      event.setDurationMs(trace.getDurationMs());
      event.setExecution(trace.toExecutionMap());
    }
    event.setOutcome(ok ? HAuditOutcome.SUCCESS : HAuditOutcome.FAILURE);
    event.setErrorMessage(errorMessage);
    if (connector != null && connector.getConnector() != null) {
      Map<String, Object> design = new LinkedHashMap<>();
      design.put("connectorName", connector.getName());
      design.put("pluginId", connector.getConnector().getPluginId());
      design.put("sourceConnectorName", connector.getConnector().getSourceConnectorName());
      event.setDesign(design);
    }
    return event;
  }

  public static HAuditEvent metadataChange(
      HAuditEventType type, String metadataKey, String name, HPrincipal principal) {
    HResourceType resourceType = mapMetadataKey(metadataKey);
    HAuditEvent event =
        HAuditEvent.of(type)
            .actor(principal != null ? principal : HSecurityContext.requirePrincipalOrAnonymous())
            .resource(HResourceRef.of(resourceType, name));
    event.setRequestId(HSecurityContext.getRequestId());
    event.setAction(actionFor(type, metadataKey));
    Map<String, Object> design = new LinkedHashMap<>();
    design.put("metadataKey", metadataKey);
    design.put("name", name);
    event.setDesign(design);
    return event;
  }

  public static Map<String, Object> buildDesignMap(
      HPresentation presentation, List<HParameter> parameters) {
    Map<String, Object> design = new LinkedHashMap<>();
    if (presentation == null) {
      return design;
    }
    design.put("presentationName", presentation.getName());
    design.put("presentationDescription", presentation.getDescription());

    List<String> parameterNames = new ArrayList<>();
    Map<String, Object> parameterValues = new LinkedHashMap<>();
    if (parameters != null) {
      for (HParameter parameter : parameters) {
        if (parameter == null || parameter.getParameterName() == null) {
          continue;
        }
        parameterNames.add(parameter.getParameterName());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("value", parameter.getParameterValue());
        value.put("redacted", false);
        parameterValues.put(parameter.getParameterName(), value);
      }
    }
    design.put("parameterNames", parameterNames);
    design.put("parameters", parameterValues);

    List<Map<String, Object>> components = new ArrayList<>();
    if (presentation.getPages() != null) {
      for (int p = 0; p < presentation.getPages().size(); p++) {
        HPage page = presentation.getPages().get(p);
        if (page == null || page.getComponents() == null) {
          continue;
        }
        for (HComponent component : page.getComponents()) {
          if (component == null || component.getComponent() == null) {
            continue;
          }
          Map<String, Object> c = new LinkedHashMap<>();
          c.put("name", component.getName());
          c.put("pluginId", component.getComponent().getPluginId());
          c.put("pageIndex", p);
          c.put("sourceConnectorName", component.getComponent().getSourceConnectorName());
          components.add(c);
        }
      }
    }
    design.put("components", components);
    // Connectors live in shared metadata; execution.connectorRuns carries runtime names/plugins.
    design.put("connectors", List.of());
    return design;
  }

  private static HResourceType mapMetadataKey(String key) {
    if (key == null) {
      return HResourceType.METADATA;
    }
    return switch (key) {
      case "presentation" -> HResourceType.PRESENTATION;
      case "connector" -> HResourceType.CONNECTOR;
      case "theme" -> HResourceType.THEME;
      case "hopper-database-connection" -> HResourceType.CONNECTION;
      default -> HResourceType.METADATA;
    };
  }

  private static String actionFor(HAuditEventType type, String metadataKey) {
    String family =
        switch (metadataKey == null ? "" : metadataKey) {
          case "presentation" -> "presentation";
          case "connector" -> "connector";
          case "theme" -> "theme";
          case "hopper-database-connection" -> "connection";
          default -> "metadata";
        };
    return switch (type) {
      case METADATA_CREATE -> family + ".create";
      case METADATA_UPDATE -> family + ".update";
      case METADATA_DELETE -> family + ".delete";
      default -> family + ".write";
    };
  }
}
