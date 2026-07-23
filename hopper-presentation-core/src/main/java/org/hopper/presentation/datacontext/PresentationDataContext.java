package org.hopper.presentation.datacontext;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
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
