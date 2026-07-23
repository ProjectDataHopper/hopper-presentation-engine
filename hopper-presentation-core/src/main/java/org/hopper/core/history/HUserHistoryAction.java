package org.hopper.core.history;

import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
@EqualsAndHashCode(of = {"objectType", "objectName"})
public class HUserHistoryAction {
  @HopMetadataProperty private String objectType;

  @HopMetadataProperty private String objectName;

  @HopMetadataProperty private Date actionDate;

  public HUserHistoryAction() {
    actionDate = new Date();
  }

  public HUserHistoryAction(String objectType, String objectName) {
    this(objectType, objectName, new Date());
  }

  public HUserHistoryAction(String objectType, String objectName, Date actionDate) {
    this.objectType = objectType;
    this.objectName = objectName;
    this.actionDate = actionDate;
  }
}
