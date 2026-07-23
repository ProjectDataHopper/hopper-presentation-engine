package org.hopper.presentation.datacontext;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.Constants;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.layout.HRenderPage;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenderPageDataContext implements IDataContext {

  private IDataContext parentDataContext;
  private HRenderPage renderPage;

  private IVariables variableSpace;

  public RenderPageDataContext(IDataContext parentDataContext, HRenderPage renderPage) {
    this.parentDataContext = parentDataContext;
    this.renderPage = renderPage;

    variableSpace = new Variables();
    variableSpace.copyFrom(parentDataContext.getVariables());

    // Inject page specific variables
    //
    variableSpace.setVariable(
        Constants.VARIABLE_PAGE_NUMBER, Integer.toString(renderPage.getPageNumber()));
  }

  @Override
  public HConnector getConnector(String name) throws HException {
    HConnector connector = parentDataContext.getConnector(name);

    // Create a copy every time someone asks for a connector.
    // This ensures that querying is safe
    //
    if (connector != null) {
      connector = new HConnector(connector);
    }
    return connector;
  }


  /**
   * Gets variableSpace
   *
   * @return value of variableSpace
   */
  @Override
  public IVariables getVariables() {
    return variableSpace;
  }


  @Override
  public IHopMetadataProvider getMetadataProvider() {
    return parentDataContext.getMetadataProvider();
  }
}
