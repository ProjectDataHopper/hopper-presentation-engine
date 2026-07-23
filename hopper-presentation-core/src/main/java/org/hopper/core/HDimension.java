package org.hopper.core;

public class HDimension extends HColumn {

  public HDimension() {
    super();
  }

  public HDimension(String columnName) {
    super(columnName);
  }

  public HDimension(
      String columnName,
      String headerValue,
      HHorizontalAlignment horizontalAlignment,
      HVerticalAlignment verticalAlignment) {
    super(columnName, headerValue, horizontalAlignment, verticalAlignment);
  }

  public HDimension(HDimension d) {
    super(d);
  }
}
