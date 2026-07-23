package org.hopper.presentation.component.types.crosstab;

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
}
