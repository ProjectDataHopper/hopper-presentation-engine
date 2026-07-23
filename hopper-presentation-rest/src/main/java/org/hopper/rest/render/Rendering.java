package org.hopper.rest.render;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.hopper.core.HSize;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.variable.HParameter;

public class Rendering implements IRendering {
  protected String id;
  protected HPresentation presentation;
  protected String presentationName;
  protected Date renderDate;
  protected List<HParameter> parameters;
  protected HLayoutResults layoutResults;

  protected Rendering() {
    this.id = UUID.randomUUID().toString();
    this.renderDate = new Date();
    this.parameters = new ArrayList<>();
  }

  /**
   * Gets id
   *
   * @return value of id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets id
   *
   * @param id value of id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Gets presentation
   *
   * @return value of presentation
   */
  @Override
  public HPresentation getPresentation() {
    return presentation;
  }

  /**
   * Sets presentation
   *
   * @param presentation value of presentation
   */
  public void setPresentation(HPresentation presentation) {
    this.presentation = presentation;
  }

  /**
   * Gets presentationName
   *
   * @return value of presentationName
   */
  @Override
  public String getPresentationName() {
    return presentationName;
  }

  /**
   * Sets presentationName
   *
   * @param presentationName value of presentationName
   */
  public void setPresentationName(String presentationName) {
    this.presentationName = presentationName;
  }

  /**
   * Gets renderDate
   *
   * @return value of renderDate
   */
  public Date getRenderDate() {
    return renderDate;
  }

  /**
   * Sets renderDate
   *
   * @param renderDate value of renderDate
   */
  public void setRenderDate(Date renderDate) {
    this.renderDate = renderDate;
  }

  /**
   * Gets parameters
   *
   * @return value of parameters
   */
  public List<HParameter> getParameters() {
    return parameters;
  }

  /**
   * Sets parameters
   *
   * @param parameters value of parameters
   */
  public void setParameters(List<HParameter> parameters) {
    this.parameters = parameters;
  }

  /**
   * Gets layoutResults
   *
   * @return value of layoutResults
   */
  public HLayoutResults getLayoutResults() {
    return layoutResults;
  }

  /**
   * Sets layoutResults
   *
   * @param layoutResults value of layoutResults
   */
  public void setLayoutResults(HLayoutResults layoutResults) {
    this.layoutResults = layoutResults;
  }
}
