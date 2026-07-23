package org.hopper.presentation.component.types.group;

import org.apache.hop.core.RowMetaAndData;
import org.hopper.core.HSize;

import java.util.ArrayList;
import java.util.List;

public class GroupDetails {

  public List<RowMetaAndData> rows;
  public List<GroupRowDetails> rowDetails;
  public HSize size;

  public GroupDetails() {
    this.rows = new ArrayList<>();
    this.rowDetails = new ArrayList<>();
  }
}
