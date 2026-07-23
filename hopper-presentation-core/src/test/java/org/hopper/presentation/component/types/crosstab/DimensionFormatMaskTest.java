package org.hopper.presentation.component.types.crosstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDimension;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFact;
import org.hopper.presentation.component.types.chart.HLineChartComponent;

/**
 * Dimension {@code formatMask} must be applied when building pivot keys so crosstab headers and
 * chart axis labels show formatted values (e.g. month {@code 1} → {@code "01"}).
 */
class DimensionFormatMaskTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void crosstabHorizontalDimensionUsesFormatMask() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    HDimension month = new HDimension("month");
    month.setFormatMask("00");
    crosstab.getHorizontalDimensions().add(month);
    crosstab.getFacts().add(new HFact("qty", AggregationMethod.SUM));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("month"));
    rowMeta.addValueMeta(new ValueMetaNumber("qty"));

    crosstab.pivotRow(rowMeta, new Object[] {1L, 10.0});
    crosstab.pivotRow(rowMeta, new Object[] {12L, 5.0});

    List<Set<String>> horizontalValues = new ArrayList<>();
    List<Set<String>> verticalValues = new ArrayList<>();
    crosstab.calculateDistinctValues(horizontalValues, verticalValues);

    assertEquals(1, horizontalValues.size());
    assertTrue(horizontalValues.get(0).contains("01"), "month 1 should format as 01");
    assertTrue(horizontalValues.get(0).contains("12"), "month 12 should format as 12");
  }

  @Test
  void chartHorizontalDimensionUsesFormatMask() throws Exception {
    HLineChartComponent chart = new HLineChartComponent();
    HDimension month = new HDimension("month");
    month.setFormatMask("00");
    chart.getHorizontalDimensions().add(month);
    chart.getFacts().add(new HFact("qty", AggregationMethod.SUM));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("month"));
    rowMeta.addValueMeta(new ValueMetaNumber("qty"));

    chart.pivotRow(rowMeta, new Object[] {3L, 1.0});
    chart.pivotRow(rowMeta, new Object[] {9L, 2.0});

    List<Set<String>> horizontalValues = new ArrayList<>();
    List<Set<String>> verticalValues = new ArrayList<>();
    chart.calculateDistinctValues(horizontalValues, verticalValues);

    assertTrue(horizontalValues.get(0).contains("03"));
    assertTrue(horizontalValues.get(0).contains("09"));
  }

  @Test
  void verticalDimensionUsesFormatMask() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    HDimension month = new HDimension("month");
    month.setFormatMask("00");
    crosstab.getVerticalDimensions().add(month);
    crosstab.getFacts().add(new HFact("qty", AggregationMethod.SUM));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("month"));
    rowMeta.addValueMeta(new ValueMetaNumber("qty"));

    crosstab.pivotRow(rowMeta, new Object[] {7L, 1.0});

    List<Set<String>> horizontalValues = new ArrayList<>();
    List<Set<String>> verticalValues = new ArrayList<>();
    crosstab.calculateDistinctValues(horizontalValues, verticalValues);

    assertTrue(verticalValues.get(0).contains("07"));
  }

  @Test
  void noFormatMaskKeepsDefaultConversion() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.getHorizontalDimensions().add(new HDimension("label"));
    crosstab.getFacts().add(new HFact("qty", AggregationMethod.SUM));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("label"));
    rowMeta.addValueMeta(new ValueMetaNumber("qty"));

    crosstab.pivotRow(rowMeta, new Object[] {"hello", 1.0});

    List<Set<String>> horizontalValues = new ArrayList<>();
    List<Set<String>> verticalValues = new ArrayList<>();
    crosstab.calculateDistinctValues(horizontalValues, verticalValues);

    assertTrue(horizontalValues.get(0).contains("hello"));
  }
}
