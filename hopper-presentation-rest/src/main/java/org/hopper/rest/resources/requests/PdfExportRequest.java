package org.hopper.rest.resources.requests;

import java.util.ArrayList;
import java.util.List;
import org.hopper.presentation.variable.HParameter;

/**
 * Request body for PDF export.
 *
 * <ul>
 *   <li>With {@code renderId} and {@code useSessionLayout=true}: export existing session pages
 *       (paginated views).
 *   <li>Otherwise: load {@code presentationName}, force paginated layout for chosen paper, render,
 *       export (required for continuous presentations).
 * </ul>
 */
public class PdfExportRequest {
  private String presentationName;
  /** Optional session render id to reuse when layout is already paginated. */
  private String renderId;
  private List<HParameter> parameters = new ArrayList<>();
  private String colorMode;

  /**
   * When true (default) and {@link #renderId} is set and the session is not continuous, export the
   * session SVG pages without re-layout.
   */
  private boolean useSessionLayout = true;

  /**
   * Paper preset: {@code current} (session / first page size), {@code a4}, {@code letter}, {@code
   * legal}, {@code a3}, {@code custom}. Continuous exports should not use {@code current} without a
   * real page size — defaults to A4 landscape when continuous.
   */
  private String paperPreset = "a4";

  private Boolean portrait;
  private Integer width;
  private Integer height;
  private Integer margin;

  public String getPresentationName() {
    return presentationName;
  }

  public void setPresentationName(String presentationName) {
    this.presentationName = presentationName;
  }

  public String getRenderId() {
    return renderId;
  }

  public void setRenderId(String renderId) {
    this.renderId = renderId;
  }

  public List<HParameter> getParameters() {
    return parameters;
  }

  public void setParameters(List<HParameter> parameters) {
    this.parameters = parameters != null ? parameters : new ArrayList<>();
  }

  public String getColorMode() {
    return colorMode;
  }

  public void setColorMode(String colorMode) {
    this.colorMode = colorMode;
  }

  public boolean isUseSessionLayout() {
    return useSessionLayout;
  }

  public void setUseSessionLayout(boolean useSessionLayout) {
    this.useSessionLayout = useSessionLayout;
  }

  public String getPaperPreset() {
    return paperPreset;
  }

  public void setPaperPreset(String paperPreset) {
    this.paperPreset = paperPreset;
  }

  public Boolean getPortrait() {
    return portrait;
  }

  public void setPortrait(Boolean portrait) {
    this.portrait = portrait;
  }

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  public Integer getMargin() {
    return margin;
  }

  public void setMargin(Integer margin) {
    this.margin = margin;
  }
}
