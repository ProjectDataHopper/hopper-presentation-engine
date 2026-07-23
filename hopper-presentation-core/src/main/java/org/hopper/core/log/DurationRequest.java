package org.hopper.core.log;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DurationRequest {
  private String startId;
  private String finishId;
  private String message;
}
