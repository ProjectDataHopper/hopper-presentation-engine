package org.hopper.presentation.component.types.crosstab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HFont;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;

@Getter
@Setter
public abstract class HBaseAggregatingComponent extends HBaseComponent implements IHComponent {

  public static final String GRANT_TOTAL_STRING = "___!GrandTotal!___";

  @HWidgetElement(
      order = "09000-horizontalDimensions",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal dimensions")
  @HopMetadataProperty
  protected List<HDimension> horizontalDimensions;

  @HWidgetElement(
      order = "09100-verticalDimensions",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical dimensions")
  @HopMetadataProperty
  protected List<HDimension> verticalDimensions;

  @HWidgetElement(
      order = "09200-facts",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Facts")
  @HopMetadataProperty
  protected List<HFact> facts;

  @HWidgetElement(
      order = "09300-showingHorizontalTotals",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show horizontal totals?")
  @HopMetadataProperty
  protected boolean showingHorizontalTotals;

  @HWidgetElement(
      order = "09400-showingVerticalTotals",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Show vertical totals?")
  @HopMetadataProperty
  protected boolean showingVerticalTotals;

  @HWidgetElement(
      order = "09500-horizontalDimensionsFont",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal dimensions font")
  @HopMetadataProperty
  private HFont horizontalDimensionsFont;

  @HWidgetElement(
      order = "09510-horizontalDimensionsColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Horizontal dimensions color")
  @HopMetadataProperty
  private HColorRGB horizontalDimensionsColor;

  @HWidgetElement(
      order = "09600-verticalDimensionsFont",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical dimensions font")
  @HopMetadataProperty
  private HFont verticalDimensionsFont;

  @HWidgetElement(
      order = "09610-verticalDimensionsColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical dimensions color")
  @HopMetadataProperty
  private HColorRGB verticalDimensionsColor;

  // Fields below are used to calculate.
  // Always make copies if you need to calculate the same component more than once.
  //
  @HWidgetElement(
      order = "09700-factsFont",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Facts font")
  @HopMetadataProperty
  private HFont factsFont;

  @HWidgetElement(
      order = "09710-factsColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Facts color")
  @HopMetadataProperty
  private HColorRGB factsColor;

  @HWidgetElement(
      order = "09800-titleFont",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Title font")
  @HopMetadataProperty
  private HFont titleFont;

  @HWidgetElement(
      order = "09810-titleColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Title color")
  @HopMetadataProperty
  private HColorRGB titleColor;

  @HWidgetElement(
      order = "09900-gridColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Grid color")
  @HopMetadataProperty
  private HColorRGB gridColor;

  @HWidgetElement(
      order = "09910-axisColor",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Axis color")
  @HopMetadataProperty
  private HColorRGB axisColor;

  @JsonIgnore protected transient List<Integer> horizontalDimensionIndexes;
  @JsonIgnore protected transient List<Integer> verticalDimensionIndexes;
  @JsonIgnore protected transient List<Integer> factIndexes;

  /**
   * Cloned value metas for dimension columns with {@link HColumn#getFormatMask()} applied (built in
   * {@link #determineColumnIndexes}).
   */
  @JsonIgnore protected transient List<IValueMeta> horizontalDimensionValueMetas;

  @JsonIgnore protected transient List<IValueMeta> verticalDimensionValueMetas;
  @JsonIgnore protected transient List<Map<List<String>, Object>> pivotMapList;
  @JsonIgnore protected transient List<Map<List<String>, Long>> countMapList;
  @JsonIgnore protected transient IRowMeta inputRowMeta;

  public HBaseAggregatingComponent() {
    this.horizontalDimensions = new ArrayList<>();
    this.verticalDimensions = new ArrayList<>();
    this.facts = new ArrayList<>();
  }

  public HBaseAggregatingComponent(String pluginId) {
    super(pluginId);
    this.horizontalDimensions = new ArrayList<>();
    this.verticalDimensions = new ArrayList<>();
    this.facts = new ArrayList<>();
  }

  public HBaseAggregatingComponent(String pluginId, HBaseAggregatingComponent c) {
    super(pluginId, c);
    this.horizontalDimensions = new ArrayList<>();
    for (HDimension d : c.horizontalDimensions) {
      this.horizontalDimensions.add(new HDimension(d));
    }
    this.verticalDimensions = new ArrayList<>();
    for (HDimension d : c.verticalDimensions) {
      this.verticalDimensions.add(new HDimension(d));
    }
    this.facts = new ArrayList<>();
    for (HFact f : c.facts) {
      this.facts.add(new HFact(f));
    }
    this.showingHorizontalTotals = c.showingHorizontalTotals;
    this.showingVerticalTotals = c.showingVerticalTotals;

    // Fonts and colors
    //
    this.horizontalDimensionsFont =
        c.horizontalDimensionsFont == null ? null : new HFont(c.horizontalDimensionsFont);
    this.horizontalDimensionsColor =
        c.horizontalDimensionsColor == null ? null : new HColorRGB(c.horizontalDimensionsColor);
    this.verticalDimensionsFont =
        c.verticalDimensionsFont == null ? null : new HFont(c.verticalDimensionsFont);
    this.verticalDimensionsColor =
        c.verticalDimensionsColor == null ? null : new HColorRGB(c.verticalDimensionsColor);
    this.factsFont = c.factsFont == null ? null : new HFont(c.factsFont);
    this.factsColor = c.factsColor == null ? null : new HColorRGB(c.factsColor);
    this.axisColor = c.axisColor == null ? null : new HColorRGB(c.axisColor);
    this.gridColor = c.gridColor == null ? null : new HColorRGB(c.gridColor);
    this.titleFont = c.titleFont == null ? null : new HFont(c.titleFont);
    this.titleColor = c.titleColor == null ? null : new HColorRGB(c.titleColor);

    // Clear transient fields
    //
    this.horizontalDimensionIndexes = null;
    this.verticalDimensionIndexes = null;
    this.factIndexes = null;
    this.horizontalDimensionValueMetas = null;
    this.verticalDimensionValueMetas = null;
    this.pivotMapList = null;
    this.countMapList = null;
    this.inputRowMeta = null;
  }

  protected void pivotRow(IRowMeta rowMeta, Object[] rowData) throws HException {
    try {
      if (factIndexes == null) {
        determineColumnIndexes(rowMeta);
      }

      // What are all the aggregations that need to be calculated?
      // One of every cell so all the vertical and horizontal combinations
      // Then one for every dimension subtotal and total
      //
      List<List<String>> keysList = new ArrayList<>();

      // Determine the keys for the horizontal & vertical axes
      // We'll be aggregating the data based on these keys...
      // Apply each dimension's format mask (e.g. month "00") so labels render correctly.
      //
      List<String> verticalKeys = new ArrayList<>();
      for (int i = 0; i < verticalDimensionIndexes.size(); i++) {
        int index = verticalDimensionIndexes.get(i);
        IValueMeta valueMeta = verticalDimensionValueMetas.get(i);
        // Null cells are common in source data; treat as empty so sort/combinations never NPE
        verticalKeys.add(nullToEmpty(valueMeta.getString(rowData[index])));
      }

      List<String> horizontalKeys = new ArrayList<>();
      for (int i = 0; i < horizontalDimensionIndexes.size(); i++) {
        int index = horizontalDimensionIndexes.get(i);
        IValueMeta valueMeta = horizontalDimensionValueMetas.get(i);
        horizontalKeys.add(nullToEmpty(valueMeta.getString(rowData[index])));
      }

      List<String> allKeys = new ArrayList<>();
      allKeys.addAll(verticalKeys);
      allKeys.addAll(horizontalKeys);

      // No dimensions: just add "-"
      //
      if (allKeys.isEmpty()) {
        allKeys.add("-");
      }

      // Add the main keys list to aggregate on
      //
      keysList.add(allKeys);

      if (showingVerticalTotals) {
        // Also on the vertical dimensions for the line totals
        //
        if (!verticalKeys.isEmpty()) {
          keysList.add(verticalKeys);
        }
      }

      if (showingHorizontalTotals) {
        // Add the horizontal dimensions for the column totals
        //
        if (!horizontalKeys.isEmpty()) {
          keysList.add(horizontalKeys);
        }
      }

      if (showingVerticalTotals && showingHorizontalTotals) {
        keysList.add(List.of(GRANT_TOTAL_STRING));
      }

      for (List<String> keys : keysList) {

        if (facts.isEmpty()) {
          Map<List<String>, Object> pivotMap = pivotMapList.getFirst();
          Map<List<String>, Long> countMap = countMapList.getFirst();
          pivotMap.put(keys, Double.valueOf(0.0));
          countMap.put(keys, Long.valueOf(0));
        } else {
          for (int i = 0; i < facts.size(); i++) {
            // Every fact is basically generating a completely different crosstab
            // with the same dimensions
            //
            Map<List<String>, Object> pivotMap = pivotMapList.get(i);
            Map<List<String>, Long> countMap = countMapList.get(i);

            IValueMeta valueMeta = rowMeta.getValueMeta(factIndexes.get(i));
            Object valueData = rowData[factIndexes.get(i)];
            HFact fact = facts.get(i);

            if (!valueMeta.isNull(valueData)) {
              // Count the values regardless...
              //
              Long count = countMap.get(keys);
              if (count == null) {
                count = 1L;
              } else {
                count++;
              }
              countMap.put(keys, count);

              //
              switch (valueMeta.getType()) {
                case IValueMeta.TYPE_NUMBER:
                  // Do some aggregation
                  Double numberValue = valueMeta.getNumber(valueData);
                  switch (fact.getAggregationMethod()) {
                    case SUM:
                    case AVERAGE:
                      Double previous = (Double) pivotMap.get(keys);
                      if (previous == null) {
                        pivotMap.put(keys, numberValue);
                      } else {
                        pivotMap.put(keys, numberValue + previous);
                      }
                      break;
                    case COUNT:
                      // Already handled
                      break;
                    default:
                      throw new HException(
                          "Number aggregation not supported yet: " + fact.getAggregationMethod());
                  }
                  break;
                case IValueMeta.TYPE_INTEGER:
                  Long integerValue = valueMeta.getInteger(valueData);
                  switch (fact.getAggregationMethod()) {
                    case SUM:
                    case AVERAGE:
                      Long previous = (Long) pivotMap.get(keys);
                      if (previous == null) {
                        pivotMap.put(keys, integerValue);
                      } else {
                        pivotMap.put(keys, integerValue + previous);
                      }
                      break;
                    case COUNT:
                      // Handled above
                      break;
                    default:
                      throw new HException(
                          "Integer aggregation not supported yet: " + fact.getAggregationMethod());
                  }
                  break;
                case IValueMeta.TYPE_BIGNUMBER:
                  BigDecimal bigValue = valueMeta.getBigNumber(valueData);
                  switch (fact.getAggregationMethod()) {
                    case SUM:
                    case AVERAGE:
                      BigDecimal previous = (BigDecimal) pivotMap.get(keys);
                      if (previous == null) {
                        pivotMap.put(keys, bigValue);
                      } else {
                        BigDecimal sum = bigValue.add(bigValue);
                        pivotMap.put(keys, sum);
                      }
                      break;
                    case COUNT:
                      // Handled above
                      break;
                    default:
                      throw new HException(
                          "BigNumber aggregation not supported yet: "
                              + fact.getAggregationMethod());
                  }
                  break;
                default:
                  if (fact.getAggregationMethod() != AggregationMethod.COUNT) {
                    throw new HException(
                        "Unsupported data type for aggregation : " + valueMeta.getName());
                  }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      try {
        throw new HException("Unable to pivot row of data : " + rowMeta.getString(rowData), e);
      } catch (HopException ex) {
        throw new HException("Unable to pivot row of data", ex);
      }
    }
  }

  protected void determineColumnIndexes(IRowMeta rowMeta) throws HException {
    factIndexes = new ArrayList<>();
    horizontalDimensionIndexes = new ArrayList<>();
    verticalDimensionIndexes = new ArrayList<>();
    horizontalDimensionValueMetas = new ArrayList<>();
    verticalDimensionValueMetas = new ArrayList<>();

    // calculate vertical dimension indexes
    //
    for (HDimension dimension : verticalDimensions) {
      int index = rowMeta.indexOfValue(dimension.getColumnName());
      if (index < 0) {
        throw new HException(
            "Vertical dimension column '" + dimension.getColumnName() + "' couldn't be found");
      }
      verticalDimensionIndexes.add(index);
      verticalDimensionValueMetas.add(dimensionValueMeta(rowMeta, index, dimension));
    }

    // calculate horizontal dimension indexes
    //
    for (HDimension dimension : horizontalDimensions) {
      int index = rowMeta.indexOfValue(dimension.getColumnName());
      if (index < 0) {
        throw new HException(
            "Horizontal dimension column '" + dimension.getColumnName() + "' couldn't be found");
      }
      horizontalDimensionIndexes.add(index);
      horizontalDimensionValueMetas.add(dimensionValueMeta(rowMeta, index, dimension));
    }

    // Calculate fact column indexes and allocate pivot map hash maps
    //
    pivotMapList = new ArrayList<>();
    countMapList = new ArrayList<>();
    for (HFact column : facts) {
      int index = rowMeta.indexOfValue(column.getColumnName());
      if (index < 0) {
        throw new HException("Fact column '" + column.getColumnName() + "' couldn't be found");
      }
      factIndexes.add(index);

      // Add an empty hash map for every metric
      //
      pivotMapList.add(new HashMap<>());
      countMapList.add(new HashMap<>());
    }

    // No facts, still keep mapping dimensions
    //
    if (facts.isEmpty()) {
      pivotMapList.add(new HashMap<>());
      countMapList.add(new HashMap<>());
    }

    // Remember rowMeta
    inputRowMeta = rowMeta;
  }

  /**
   * Clone the source value meta and apply the dimension's format mask so labels use it when
   * converting values to strings (e.g. integer month {@code 1} → {@code "01"} with mask {@code
   * "00"}).
   */
  private static IValueMeta dimensionValueMeta(IRowMeta rowMeta, int index, HColumn dimension) {
    IValueMeta valueMeta = rowMeta.getValueMeta(index).clone();
    String formatMask = dimension.getFormatMask();
    if (formatMask != null && !formatMask.isEmpty()) {
      valueMeta.setConversionMask(formatMask);
    }
    return valueMeta;
  }

  protected void getCombinations(
      List<Set<String>> setsList,
      int index,
      Set<List<String>> combinations,
      List<String> currentRow) {
    if (setsList.isEmpty()) {
      return;
    }
    if (index >= setsList.size()) {
      // add the current row to the set of combinations
      // Make a copy!
      //
      combinations.add(new ArrayList<>(currentRow));
      return;
    }

    // Consider all values in the horizontal dimension in the given column.
    //
    Set<String> values = setsList.get(index);
    for (String value : values) {
      currentRow.add(value);
      getCombinations(setsList, index + 1, combinations, currentRow);
      // Remove the last row
      currentRow.removeLast();
    }
  }

  protected List<List<String>> sortCombinations(Set<List<String>> horizontalCombinations) {
    List<List<String>> sortedHorizontalCombinations = new ArrayList<>(horizontalCombinations);
    sortListOfListOfStrings(sortedHorizontalCombinations);
    return sortedHorizontalCombinations;
  }

  protected void sortListOfListOfStrings(List<List<String>> listOfListOfStrings) {
    if (listOfListOfStrings == null || listOfListOfStrings.size() < 2) {
      return;
    }
    listOfListOfStrings.sort(
        (list1, list2) -> {
          if (list1 == list2) {
            return 0;
          }
          if (list1 == null) {
            return -1;
          }
          if (list2 == null) {
            return 1;
          }
          int n = Math.min(list1.size(), list2.size());
          for (int i = 0; i < n; i++) {
            int cmp = compareNullableStrings(list1.get(i), list2.get(i));
            if (cmp != 0) {
              return cmp;
            }
          }
          return Integer.compare(list1.size(), list2.size());
        });
  }

  /** Null-safe string compare; null sorts before non-null. */
  static int compareNullableStrings(String one, String two) {
    if (one == null) {
      return two == null ? 0 : -1;
    }
    if (two == null) {
      return 1;
    }
    return one.compareTo(two);
  }

  static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  protected void calculateDistinctValues(
      List<Set<String>> horizontalValues, List<Set<String>> verticalValues) {
    for (int i = 0; i < horizontalDimensions.size(); i++) {
      horizontalValues.add(new HashSet<>());
    }
    for (int i = 0; i < verticalDimensions.size(); i++) {
      verticalValues.add(new HashSet<>());
    }

    // So we calculate distinct values for all dimensions...
    //
    for (Map<List<String>, Object> pivotMap : pivotMapList) {
      // So if we take the keys in the pivotMap, the first values
      // are the vertical dimensions.
      // Then we'll find the horizontal dimensions.
      //
      // HOWEVER, we need to sort and draw all distinct values.
      //
      for (List<String> keys : pivotMap.keySet()) {
        // Avoid picking up the aggregates
        //
        if (keys.size() == verticalValues.size() + horizontalValues.size()) {
          for (int i = 0; i < verticalValues.size(); i++) {
            // Create a unique list of values for the horizontal dimensions...
            //
            verticalValues.get(i).add(keys.get(i));
          }
          for (int i = 0; i < horizontalValues.size(); i++) {
            // Also get a list of unique values over the vertical dimensions...
            //
            horizontalValues.get(i).add(keys.get(verticalDimensions.size() + i));
          }
        }
      }
    }
  }

  protected HColorRGB lookupVerticalDimensionsColor(IRenderContext renderContext)
      throws HException {
    if (verticalDimensionsColor != null) {
      return verticalDimensionsColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupVerticalDimensionsColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException(
        "No vertical dimensions color nor default color defined (no theme used or found)");
  }

  protected HFont lookupVerticalDimensionsFont(IRenderContext renderContext) throws HException {
    if (verticalDimensionsFont != null) {
      return verticalDimensionsFont;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupVerticalDimensionsFont();
    }
    if (getDefaultFont() != null) {
      return getDefaultFont();
    }
    throw new HException(
        "No vertical dimensions font nor default font defined (no theme used or found)");
  }

  protected HColorRGB lookupHorizontalDimensionsColor(IRenderContext renderContext)
      throws HException {
    if (horizontalDimensionsColor != null) {
      return horizontalDimensionsColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupHorizontalDimensionsColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException(
        "No horizontal dimensions color nor default color defined (no theme used or found)");
  }

  protected HFont lookupHorizontalDimensionsFont(IRenderContext renderContext) throws HException {
    if (horizontalDimensionsFont != null) {
      return horizontalDimensionsFont;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupHorizontalDimensionsFont();
    }
    if (getDefaultFont() != null) {
      return getDefaultFont();
    }
    throw new HException(
        "No horizontal dimensions font nor default font defined (no theme used or found)");
  }

  /** Configured horizontal dimension column names (non-blank only). */
  @JsonIgnore
  protected List<String> horizontalDimensionColumnNames() {
    return dimensionColumnNames(horizontalDimensions);
  }

  /** Configured vertical dimension column names (non-blank only). */
  @JsonIgnore
  protected List<String> verticalDimensionColumnNames() {
    return dimensionColumnNames(verticalDimensions);
  }

  /** Horizontal then vertical dimension column names (deduplicated, non-blank). */
  @JsonIgnore
  protected List<String> allDimensionColumnNames() {
    List<String> names = new ArrayList<>(horizontalDimensionColumnNames());
    for (String name : verticalDimensionColumnNames()) {
      if (!names.contains(name)) {
        names.add(name);
      }
    }
    return names;
  }

  private static List<String> dimensionColumnNames(List<? extends HColumn> dimensions) {
    List<String> names = new ArrayList<>();
    if (dimensions == null) {
      return names;
    }
    for (HColumn dim : dimensions) {
      if (dim == null || StringUtils.isBlank(dim.getColumnName())) {
        continue;
      }
      String name = dim.getColumnName().trim();
      if (!names.contains(name)) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * Build {@link org.hopper.core.HColumn} copies for the given dimension column names (for {@link
   * org.hopper.core.draw.DrawnContext} matching).
   */
  @JsonIgnore
  protected List<HColumn> dimensionColumnsForNames(List<String> names) {
    List<HColumn> cols = new ArrayList<>();
    if (names == null) {
      return cols;
    }
    for (String name : names) {
      if (StringUtils.isNotBlank(name)) {
        cols.add(new HColumn(name.trim()));
      }
    }
    return cols;
  }

  protected HColorRGB lookupFactsColor(IRenderContext renderContext) throws HException {
    if (factsColor != null) {
      return factsColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupFactsColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException("No facts color nor default color defined (no theme used or found)");
  }

  protected HFont lookupFactsFont(IRenderContext renderContext) throws HException {
    if (factsFont != null) {
      return factsFont;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupFactsFont();
    }
    if (getDefaultFont() != null) {
      return getDefaultFont();
    }
    throw new HException("No facts font nor default font defined (no theme used or found)");
  }

  protected HColorRGB lookupTitleColor(IRenderContext renderContext) throws HException {
    if (titleColor != null) {
      return titleColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupTitleColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException("No title color nor default color defined (no theme used or found)");
  }

  protected HFont lookupTitleFont(IRenderContext renderContext) throws HException {
    if (titleFont != null) {
      return titleFont;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupTitleFont();
    }
    if (getDefaultFont() != null) {
      return getDefaultFont();
    }
    throw new HException("No title font nor default font defined (no theme used or found)");
  }

  protected HColorRGB lookupAxisColor(IRenderContext renderContext) throws HException {
    if (axisColor != null) {
      return axisColor;
    }
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupAxisColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException("No axis color nor default color defined (no theme used or found)");
  }

  protected HColorRGB lookupGridColor(IRenderContext renderContext) throws HException {
    if (gridColor != null) {
      return gridColor;
    }
    HColorRGB color = null;
    HTheme theme = renderContext.lookupTheme(themeName);
    if (theme != null) {
      return theme.lookupGridColor();
    }
    if (getDefaultColor() != null) {
      return getDefaultColor();
    }
    throw new HException("No grid color nor default color defined (no theme used or found)");
  }
}
