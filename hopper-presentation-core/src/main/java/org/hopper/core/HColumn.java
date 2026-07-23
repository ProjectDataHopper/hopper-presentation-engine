package org.hopper.core;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
public class HColumn {

  @HopMetadataProperty private String columnName;

  @HopMetadataProperty private String headerValue;

  /**
   * May be null after incomplete form/metadata saves; {@link #getHorizontalAlignment()} always
   * returns a non-null default.
   */
  @Getter(AccessLevel.NONE)
  @HopMetadataProperty
  private HHorizontalAlignment horizontalAlignment;

  /**
   * May be null after incomplete form/metadata saves; {@link #getVerticalAlignment()} always
   * returns a non-null default.
   */
  @Getter(AccessLevel.NONE)
  @HopMetadataProperty
  private HVerticalAlignment verticalAlignment;

  /**
   * Explicit column width in pixels for table layout. {@code 0} (default) means auto-detect from
   * header/cell content when drawing. A positive value is used as the column width when rendering
   * tables.
   */
  @HopMetadataProperty private int width;

  @HopMetadataProperty private String formatMask;

  @HopMetadataProperty @Deprecated private HFont font;

  public HColumn() {
    horizontalAlignment = HHorizontalAlignment.LEFT;
    verticalAlignment = HVerticalAlignment.TOP;
  }

  public HColumn(HColumn c) {
    this.columnName = c.columnName;
    this.headerValue = c.headerValue;
    this.horizontalAlignment = c.horizontalAlignment;
    this.verticalAlignment = c.verticalAlignment;
    this.width = c.width;
    this.formatMask = c.formatMask;
  }

  public HColumn(String columnName) {
    this();
    this.columnName = columnName;
  }

  public HColumn(
      String columnName,
      String headerValue,
      HHorizontalAlignment horizontalAlignment,
      HVerticalAlignment verticalAlignment) {
    this.columnName = columnName;
    this.headerValue = headerValue;
    this.horizontalAlignment = horizontalAlignment;
    this.verticalAlignment = verticalAlignment;
  }

  public HHorizontalAlignment getHorizontalAlignment() {
    return horizontalAlignment != null ? horizontalAlignment : HHorizontalAlignment.LEFT;
  }

  public HVerticalAlignment getVerticalAlignment() {
    return verticalAlignment != null ? verticalAlignment : HVerticalAlignment.TOP;
  }
}
