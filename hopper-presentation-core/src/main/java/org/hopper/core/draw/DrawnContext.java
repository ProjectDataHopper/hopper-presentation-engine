package org.hopper.core.draw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Dimension column name → value for the clicked item (e.g. region→EMEA, year→2024). Used by
   * interaction actions to set multiple parameters from a crosstab (or similar) hit.
   */
  private Map<String, String> dimensionValues;

  public DrawnContext() {
    this.dimensions = new ArrayList<>();
    this.dimensionValues = new LinkedHashMap<>();
  }

  public DrawnContext(String value) {
    this();
    this.value = value;
  }

  public DrawnContext(List<HColumn> dimensions, String value) {
    this();
    this.dimensions = dimensions != null ? dimensions : new ArrayList<>();
    this.value = value;
  }

  public DrawnContext(List<HColumn> dimensions, String value, Map<String, String> dimensionValues) {
    this(dimensions, value);
    if (dimensionValues != null) {
      this.dimensionValues.putAll(dimensionValues);
    }
  }

  public DrawnContext(String value, HColumn... dimensions) {
    this(value);
    this.dimensions.addAll(Arrays.asList(dimensions));
  }

  /** Lookup a dimension value by column name (null if missing). */
  public String getDimensionValue(String columnName) {
    if (dimensionValues == null || columnName == null) {
      return null;
    }
    return dimensionValues.get(columnName);
  }
}
