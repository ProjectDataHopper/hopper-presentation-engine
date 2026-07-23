package org.hopper.presentation.component.types.table;

import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.HTextGeometry;

import java.util.ArrayList;
import java.util.List;

public class TableDetails {
  public List<RowMetaAndData> rows;
  public List<List<HTextGeometry>> columnSizesList;
  public List<List<String>> rowStringsList;
  public List<Integer> maxWidths;
  public List<Integer> maxHeights;
  public IRowMeta rowMeta;
  public int totalWidth;
  public int totalHeight;

  public TableDetails() {
    rows = new ArrayList<>();
    columnSizesList = new ArrayList<>();
    rowStringsList = new ArrayList<>();
    maxWidths = new ArrayList<>();
    maxHeights = new ArrayList<>();
    maxHeights = new ArrayList<>();
  }
}
