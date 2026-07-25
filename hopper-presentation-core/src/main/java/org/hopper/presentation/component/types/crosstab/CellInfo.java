package org.hopper.presentation.component.types.crosstab;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.core.HColumn;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HTextGeometry;
import org.hopper.core.HVerticalAlignment;

public class CellInfo {
  public HTextGeometry geometry;
  public String text;
  public HColumn column;
  public HVerticalAlignment verticalAlignment;
  public HHorizontalAlignment horizontalAlignment;
  public IValueMeta valueMeta;
  public Object valueData;

  /**
   * Dimension column name → display value for this cell's intersection (used for interaction
   * parameter mapping). Empty for pure header chrome cells without a clear intersection.
   */
  public Map<String, String> dimensionValues = new LinkedHashMap<>();

  public CellInfo() {}

  public CellInfo(
      HTextGeometry geometry,
      String text,
      HColumn column,
      HVerticalAlignment verticalAlignment,
      HHorizontalAlignment horizontalAlignment) {
    this.geometry = geometry;
    this.text = text;
    this.column = column;
    this.verticalAlignment = verticalAlignment;
    this.horizontalAlignment = horizontalAlignment;
  }

  public CellInfo withDimensionValues(Map<String, String> values) {
    if (values != null) {
      this.dimensionValues = new LinkedHashMap<>(values);
    }
    return this;
  }
}
