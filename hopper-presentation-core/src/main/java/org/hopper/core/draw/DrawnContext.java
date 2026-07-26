package org.hopper.core.draw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.HColumn;

@Getter
@Setter
@ToString
public class DrawnContext {

  private List<HColumn> dimensions;
  private String value;

  /**
   * Dimension column name → value for the clicked item (e.g. region→EMEA, year→2024). Used by
   * interaction actions to set multiple parameters from a chart, table, or crosstab hit.
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

  /**
   * Hit with dimension columns and a primary display value. When there is exactly one named
   * dimension column, also seeds {@link #dimensionValues} so interaction {@code
   * dimensionParameters} (e.g. company_name → COMPANY_NAME) resolve without a separate map.
   */
  public DrawnContext(List<HColumn> dimensions, String value) {
    this();
    this.dimensions = dimensions != null ? new ArrayList<>(dimensions) : new ArrayList<>();
    this.value = value;
    seedSingleDimensionValue(value);
  }

  public DrawnContext(List<HColumn> dimensions, String value, Map<String, String> dimensionValues) {
    this();
    this.dimensions = dimensions != null ? new ArrayList<>(dimensions) : new ArrayList<>();
    this.value = value;
    if (dimensionValues != null) {
      this.dimensionValues.putAll(dimensionValues);
    } else {
      seedSingleDimensionValue(value);
    }
  }

  public DrawnContext(String value, HColumn... dimensions) {
    this(dimensions != null ? Arrays.asList(dimensions) : List.of(), value);
  }

  /** Lookup a dimension value by column name (null if missing). */
  public String getDimensionValue(String columnName) {
    if (dimensionValues == null || columnName == null) {
      return null;
    }
    return dimensionValues.get(columnName);
  }

  /**
   * Map dimension columns to combination values (same order). Skips blank column names. Useful for
   * multi-field chart axes and crosstab intersections.
   */
  public static Map<String, String> mapDimensionValues(
      List<? extends HColumn> columns, List<String> combination) {
    Map<String, String> map = new LinkedHashMap<>();
    if (columns == null || combination == null) {
      return map;
    }
    for (int i = 0; i < columns.size() && i < combination.size(); i++) {
      HColumn dim = columns.get(i);
      if (dim != null && StringUtils.isNotBlank(dim.getColumnName())) {
        String v = combination.get(i);
        map.put(dim.getColumnName(), v != null ? v : "");
      }
    }
    return map;
  }

  /**
   * When only one dimension column is present, map the primary hit value to that column so
   * parameter mappings work for simple tables and single-dimension charts.
   */
  private void seedSingleDimensionValue(String primaryValue) {
    if (primaryValue == null || dimensions == null || dimensions.size() != 1) {
      return;
    }
    HColumn c = dimensions.get(0);
    if (c == null || StringUtils.isBlank(c.getColumnName())) {
      return;
    }
    if (dimensionValues == null) {
      dimensionValues = new LinkedHashMap<>();
    }
    // Do not overwrite an explicit map entry
    if (!dimensionValues.containsKey(c.getColumnName())) {
      dimensionValues.put(c.getColumnName(), primaryValue);
    }
  }
}
