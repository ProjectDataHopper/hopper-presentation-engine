package org.hopper.core.draw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hopper.core.HColumn;

@Getter
@Setter
@ToString
public class DrawnContext {

  private List<HColumn> dimensions;
  private String value;

  public DrawnContext() {
    this.dimensions = new ArrayList<>();
  }

  public DrawnContext(String value) {
    this();
    this.value = value;
  }

  public DrawnContext(List<HColumn> dimensions, String value) {
    this.dimensions = dimensions;
    this.value = value;
  }

  public DrawnContext(String value, HColumn... dimensions) {
    this(value);
    this.dimensions.addAll(Arrays.asList(dimensions));
  }
}
