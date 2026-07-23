package org.hopper.core;

import org.apache.hop.core.row.IRowMeta;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HDataSet {
  private IRowMeta rowMeta;

  private List<Object[]> rows;

  public HDataSet() {
    rows = new ArrayList<>();
  }

  public HDataSet(IRowMeta rowMeta, List<Object[]> rows) {
    this.rowMeta = rowMeta;
    this.rows = rows;
  }
}
