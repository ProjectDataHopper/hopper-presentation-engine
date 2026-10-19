package org.hopper.presentation.component.types.table;

import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.hopper.core.HTextGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TableDetails {
  public List<RowMetaAndData> rows;
  public List<List<HTextGeometry>> columnSizesList;
  public List<List<String>> rowStringsList;

  /**
   * Parallel to {@link #rowStringsList}. Each entry is every field of that source row, including
   * columns the table does not display. The header entry is empty.
   */
  public List<Map<String, String>> rowFieldValues;

  public List<Integer> maxWidths;
  public List<Integer> maxHeights;
  public IRowMeta rowMeta;
  public int totalWidth;
  public int totalHeight;

  public TableDetails() {
    rows = new ArrayList<>();
    columnSizesList = new ArrayList<>();
    rowStringsList = new ArrayList<>();
    rowFieldValues = new ArrayList<>();
    maxWidths = new ArrayList<>();
    maxHeights = new ArrayList<>();
  }
}
