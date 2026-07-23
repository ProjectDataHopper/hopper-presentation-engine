package org.hopper.rest.resources.responses;

public class PresentationResponse {
  private String name;
  private String description;
  private String virtualPath;

  public PresentationResponse() {}

  public PresentationResponse(String name, String description) {
    this(name, description, null);
  }

  public PresentationResponse(String name, String description, String virtualPath) {
    this.name = name;
    this.description = description;
    this.virtualPath = virtualPath;
  }

  /**
   * Gets name
   *
   * @return value of name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets name
   *
   * @param name value of name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets description
   *
   * @return value of description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets description
   *
   * @param description value of description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets virtualPath
   *
   * @return Hop metadata virtual path (folder classification), or null/empty for root
   */
  public String getVirtualPath() {
    return virtualPath;
  }

  /**
   * Sets virtualPath
   *
   * @param virtualPath value of virtualPath
   */
  public void setVirtualPath(String virtualPath) {
    this.virtualPath = virtualPath;
  }
}
