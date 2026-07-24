package org.hopper.rest.resources.requests;

import java.util.ArrayList;
import java.util.List;
import org.hopper.presentation.variable.HParameter;

public class RenderPresentationRequest {
  private String presentationName;
  private List<HParameter> parameters;
  private boolean reload;
  /** Optional: {@code light} or {@code dark}. Defaults to light when blank. */
  private String colorMode;

  public RenderPresentationRequest() {
    this.parameters = new ArrayList<>();
  }

  /**
   * Gets presentationName
   *
   * @return value of presentationName
   */
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
   * Gets reload
   *
   * @return value of reload
   */
  public boolean isReload() {
    return reload;
  }

  /**
   * Sets reload
   *
   * @param reload value of reload
   */
  public void setReload(boolean reload) {
    this.reload = reload;
  }

  public String getColorMode() {
    return colorMode;
  }

  public void setColorMode(String colorMode) {
    this.colorMode = colorMode;
  }
}
