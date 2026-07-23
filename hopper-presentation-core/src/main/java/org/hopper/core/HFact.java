package org.hopper.core;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
public class HFact extends HColumn {

  @HopMetadataProperty private AggregationMethod aggregationMethod;

  @HopMetadataProperty private boolean horizontalAggregation;

  @HopMetadataProperty private String horizontalAggregationHeader;

  @HopMetadataProperty private boolean verticalAggregation;

  @HopMetadataProperty private String verticalAggregationHeader;

  @Getter(AccessLevel.NONE)
  @HopMetadataProperty
  private HHorizontalAlignment headerHorizontalAlignment;

  @Getter(AccessLevel.NONE)
  @HopMetadataProperty
  private HVerticalAlignment headerVerticalAlignment;

  public HFact() {
    super();
    headerHorizontalAlignment = HHorizontalAlignment.LEFT;
    headerVerticalAlignment = HVerticalAlignment.TOP;
  }

  public HFact(String columnName, AggregationMethod aggregationMethod) {
    super(columnName);
    this.aggregationMethod = aggregationMethod;
  }

  public HFact(
      String columnName,
      String headerValue,
      HHorizontalAlignment horizontalAlignment,
      HVerticalAlignment verticalAlignment,
      AggregationMethod aggregationMethod,
      String formatMask) {
    super(columnName, headerValue, horizontalAlignment, verticalAlignment);
    this.aggregationMethod = aggregationMethod;
    setFormatMask(formatMask);
  }

  public HFact(HFact f) {
    super(f);
    this.aggregationMethod = f.aggregationMethod;
    this.horizontalAggregation = f.horizontalAggregation;
    this.horizontalAggregationHeader = f.horizontalAggregationHeader;
    this.verticalAggregation = f.verticalAggregation;
    this.verticalAggregationHeader = f.verticalAggregationHeader;
    this.headerHorizontalAlignment = f.headerHorizontalAlignment;
    this.headerVerticalAlignment = f.headerVerticalAlignment;
  }

  public HHorizontalAlignment getHeaderHorizontalAlignment() {
    return headerHorizontalAlignment != null
        ? headerHorizontalAlignment
        : HHorizontalAlignment.LEFT;
  }

  public HVerticalAlignment getHeaderVerticalAlignment() {
    return headerVerticalAlignment != null ? headerVerticalAlignment : HVerticalAlignment.TOP;
  }
}
