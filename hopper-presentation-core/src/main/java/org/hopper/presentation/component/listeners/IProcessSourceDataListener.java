package org.hopper.presentation.component.listeners;

import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

public interface IProcessSourceDataListener {
  /**
   * The processSourceData() method of a component is about to be called.
   * Before that happens we give the chance to call this method through the listener in the component.
   *
   * @param presentation
   * @param page
   * @param component
   * @param dataContext
   * @param renderContext
   * @param results
   * @throws HException
   */
  void beforeProcessSourceDataCalled(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException;
}
