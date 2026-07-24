package org.hopper.presentation.datacontext;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.Constants;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HCachingConnector;
import org.hopper.presentation.connector.type.IHConnector;
import lombok.Getter;
import lombok.Setter;

/** A data context with variables; connectors resolve from metadata only. */
@Getter
@Setter
public class PresentationDataContext implements IDataContext {

  private HPresentation presentation;

  private IVariables variables;

  private IHopMetadataProvider metadataProvider;

  /** Optional lineage collector for the current layout/render request. */
  private HExecutionTrace executionTrace = HExecutionTrace.noop();

  /**
   * Per-layout cache of connector query results so multiple components sharing a connector name
   * only stream once.
   */
  private HConnectorResultCache connectorResultCache = HConnectorResultCache.fromRuntimeSettings();

  /** Metrics-enabled presentation log channel for the current layout/render. */
  private ILogChannel logChannel;

  @Override
  public ILogChannel getLogChannel() {
    return logChannel;
  }

  public PresentationDataContext(
      HPresentation presentation, IHopMetadataProvider metadataProvider) {
    this(presentation, metadataProvider, null);
  }

  /**
   * @param parentVariables optional system/server variable space to inherit (e.g. admin system
   *     variables). Presentation-specific values and parameters override parent entries.
   */
  public PresentationDataContext(
      HPresentation presentation,
      IHopMetadataProvider metadataProvider,
      IVariables parentVariables) {
    this.presentation = presentation;
    this.metadataProvider = metadataProvider;
    variables = new Variables();
    IVariables parent =
        parentVariables != null ? parentVariables : HGlobalVariables.get();
    if (parent != null) {
      // Copy parent values (system variables). Prefer copyFrom over initializeFrom so we do not
      // flood the context with every JVM system property when a parent is already prepared.
      variables.copyFrom(parent);
    }

    variables.setVariable(Constants.VARIABLE_PRESENTATION_NAME, presentation.getName());
    variables.setVariable(
        Constants.VARIABLE_PRESENTATION_DESCRIPTION, presentation.getDescription());
    variables.setVariable(
        Constants.VARIABLE_SYSTEM_DATE, new SimpleDateFormat("yyyy/MM/dd").format(new Date()));
    // Ship fleet URL for ops REST connectors (${HOPPER_SHIP_API_URL}/api/runs)
    // Only set defaults when not already provided by parent/system variables.
    if (StringUtils.isBlank(variables.getVariable("HOPPER_SHIP_API_URL"))) {
      String shipApi = firstEnvOrProp("HOPPER_SHIP_API_URL", "http://localhost:20000");
      while (shipApi.endsWith("/")) {
        shipApi = shipApi.substring(0, shipApi.length() - 1);
      }
      variables.setVariable("HOPPER_SHIP_API_URL", shipApi);
    }

    if (StringUtils.isBlank(variables.getVariable("HOPPER_METADATA_PATH"))) {
      String metaPath = firstEnvOrProp("HOPPER_METADATA_PATH", "");
      if (StringUtils.isNotBlank(metaPath)) {
        while (metaPath.endsWith("/") || metaPath.endsWith("\\")) {
          metaPath = metaPath.substring(0, metaPath.length() - 1);
        }
        variables.setVariable("HOPPER_METADATA_PATH", metaPath);
      }
    }
  }

  private static String firstEnvOrProp(String key, String defaultValue) {
    String v = System.getenv(key);
    if (StringUtils.isBlank(v)) {
      v = System.getProperty(key, "");
    }
    return StringUtils.isNotBlank(v) ? v.trim() : defaultValue;
  }

  @Override
  public HExecutionTrace getExecutionTrace() {
    return executionTrace != null ? executionTrace : HExecutionTrace.noop();
  }

  @Override
  public HConnectorResultCache getConnectorResultCache() {
    return connectorResultCache;
  }

  @Override
  public HConnector getConnector(String name) throws HException {
    // Palette-dropped charts/tables often have null/blank sourceConnectorName until configured.
    // Never call the metadata serializer with a blank name (Hop throws).
    if (StringUtils.isBlank(name)) {
      return null;
    }

    HConnector connector;
    try {
      connector = metadataProvider.getSerializer(HConnector.class).load(name);
    } catch (HopException e) {
      throw new HException("Error loading Hopper connector '" + name + "' from metadata", e);
    }

    // Create a copy every time someone asks for a connector.
    // This ensures that querying is safe
    //
    if (connector != null) {
      connector = new HConnector(connector);
      // Wrap so multiple components that load this catalog name share one stream result
      if (connectorResultCache != null && connectorResultCache.isEnabled()) {
        IHConnector plugin = connector.getConnector();
        if (plugin != null) {
          connector.setConnector(HCachingConnector.wrapIfNeeded(name, plugin));
        }
      }
    }
    return connector;
  }
}
