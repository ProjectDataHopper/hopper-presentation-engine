package org.hopper.rest.resources.requests;

/** The vectors for an actions request in the Render resource. */
public class ActionsRequest {
  private String renderId;
  private int pageNumber;
  private int x;
  private int y;

  /**
   * Optional interaction method filter ({@code SINGLE_CLICK}, {@code DOUBLE_CLICK}, {@code
   * MOUSE_HOVER}). When blank, all methods for the hit are returned in {@code matches}.
   */
  private String method;

  /**
   * Presentation metadata name for rebuild-on-miss when {@link #renderId} was purged from the render
   * cache (short TTL / restart).
   */
  private String presentationName;

  /** Optional light/dark for rebuild-on-miss. */
  private String colorMode;

  /** Optional {@code continuous} / {@code paginated} for rebuild-on-miss. */
  private String layoutMode;

  /** Optional continuous viewport width for rebuild-on-miss. */
  private Integer viewportWidth;

  public ActionsRequest() {}

  @Override
  public String toString() {
    return "ActionsRequest{"
        + "renderId='"
        + renderId
        + '\''
        + ", pageNumber="
        + pageNumber
        + ", x="
        + x
        + ", y="
        + y
        + ", method='"
        + method
        + '\''
        + ", presentationName='"
        + presentationName
        + '\''
        + '}';
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getPresentationName() {
    return presentationName;
  }

  public void setPresentationName(String presentationName) {
    this.presentationName = presentationName;
  }

  public String getColorMode() {
    return colorMode;
  }

  public void setColorMode(String colorMode) {
    this.colorMode = colorMode;
  }

  public String getLayoutMode() {
    return layoutMode;
  }

  public void setLayoutMode(String layoutMode) {
    this.layoutMode = layoutMode;
  }

  public Integer getViewportWidth() {
    return viewportWidth;
  }

  public void setViewportWidth(Integer viewportWidth) {
    this.viewportWidth = viewportWidth;
  }

  /**
   * Gets renderId
   *
   * @return value of renderId
   */
  public String getRenderId() {
    return renderId;
  }

  /**
   * Sets renderId
   *
   * @param renderId value of renderId
   */
  public void setRenderId(String renderId) {
    this.renderId = renderId;
  }

  /**
   * Gets pageNumber
   *
   * @return value of pageNumber
   */
  public int getPageNumber() {
    return pageNumber;
  }

  /**
   * Sets pageNumber
   *
   * @param pageNumber value of pageNumber
   */
  public void setPageNumber(int pageNumber) {
    this.pageNumber = pageNumber;
  }

  /**
   * Gets x
   *
   * @return value of x
   */
  public int getX() {
    return x;
  }

  /**
   * Sets x
   *
   * @param x value of x
   */
  public void setX(int x) {
    this.x = x;
  }

  /**
   * Gets y
   *
   * @return value of y
   */
  public int getY() {
    return y;
  }

  /**
   * Sets y
   *
   * @param y value of y
   */
  public void setY(int y) {
    this.y = y;
  }
}
