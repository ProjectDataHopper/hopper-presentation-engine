package org.hopper.presentation.datacontext;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.Constants;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
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

  public PresentationDataContext(
      HPresentation presentation, IHopMetadataProvider metadataProvider) {
    this.presentation = presentation;
    this.metadataProvider = metadataProvider;
    variables = new Variables();

    variables.setVariable(Constants.VARIABLE_PRESENTATION_NAME, presentation.getName());
    variables.setVariable(
        Constants.VARIABLE_PRESENTATION_DESCRIPTION, presentation.getDescription());
    variables.setVariable(
        Constants.VARIABLE_SYSTEM_DATE, new SimpleDateFormat("yyyy/MM/dd").format(new Date()));
    // Ship fleet URL for ops REST connectors (${HOPPER_SHIP_API_URL}/api/runs)
    String shipApi = firstEnvOrProp("HOPPER_SHIP_API_URL", "http://localhost:20000");
    while (shipApi.endsWith("/")) {
      shipApi = shipApi.substring(0, shipApi.length() - 1);
    }
    variables.setVariable("HOPPER_SHIP_API_URL", shipApi);

    String metaPath = firstEnvOrProp("HOPPER_METADATA_PATH", "");
    if (StringUtils.isNotBlank(metaPath)) {
      while (metaPath.endsWith("/") || metaPath.endsWith("\\")) {
        metaPath = metaPath.substring(0, metaPath.length() - 1);
      }
      variables.setVariable("HOPPER_METADATA_PATH", metaPath);
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
    }
    return connector;
  }
}
