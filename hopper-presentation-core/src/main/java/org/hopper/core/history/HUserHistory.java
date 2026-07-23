package org.hopper.core.history;

import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@HopMetadata(
    key = "user-history",
    name = "Hopper User History",
    description = "Describes user action history")
@Getter
@Setter
public class HUserHistory extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private List<HUserHistoryAction> actions;

  public HUserHistory() {
    actions = new ArrayList<>();
  }

  public HUserHistory(String name, List<HUserHistoryAction> actions) {
    this.name = name;
    this.actions = actions;
  }
}
