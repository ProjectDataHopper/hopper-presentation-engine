package org.hopper.presentation.component.types.crosstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBigNumber;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDimension;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFact;
import org.hopper.presentation.layout.HAggregatingRuntimeState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pre-aggregated facts must keep weighted averages and real BigNumber sums. */
class PreAggregatedFactPivotTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void weightedAverageGrandTotalUsesRowCounts() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.setShowingHorizontalTotals(true);
    crosstab.setShowingVerticalTotals(true);
    crosstab.getVerticalDimensions().add(new HDimension("channel"));
    crosstab.getHorizontalDimensions().add(new HDimension("year"));
    HFact amount = new HFact("amount", AggregationMethod.AVERAGE);
    amount.setWeightColumnName("amount_n");
    crosstab.getFacts().add(amount);

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("channel"));
    rowMeta.addValueMeta(new ValueMetaString("year"));
    rowMeta.addValueMeta(new ValueMetaNumber("amount"));
    rowMeta.addValueMeta(new ValueMetaInteger("amount_n"));

    crosstab.pivotRow(rowMeta, new Object[] {"A", "2024", 10.0, 1L});
    crosstab.pivotRow(rowMeta, new Object[] {"B", "2024", 0.0, 100L});

    Map<List<String>, Object> sums = crosstab.getPivotMapList().get(0);
    Map<List<String>, Long> counts = crosstab.getCountMapList().get(0);
    assertEquals(10.0, sums.get(List.of("A", "2024")));
    assertEquals(1L, counts.get(List.of("A", "2024")));
    assertEquals(0.0, sums.get(List.of("B", "2024")));
    assertEquals(100L, counts.get(List.of("B", "2024")));
    assertEquals(10.0, sums.get(List.of(HBaseAggregatingComponent.GRANT_TOTAL_STRING)));
    assertEquals(101L, counts.get(List.of(HBaseAggregatingComponent.GRANT_TOTAL_STRING)));
  }

  @Test
  void bigNumberSumsAddThePreviousTotal() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.getVerticalDimensions().add(new HDimension("channel"));
    crosstab.getFacts().add(new HFact("amount", AggregationMethod.SUM));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("channel"));
    rowMeta.addValueMeta(new ValueMetaBigNumber("amount"));

    crosstab.pivotRow(rowMeta, new Object[] {"A", new BigDecimal("10")});
    crosstab.pivotRow(rowMeta, new Object[] {"A", new BigDecimal("20")});

    Object stored = crosstab.getPivotMapList().get(0).get(List.of("A"));
    assertEquals(0, new BigDecimal("30").compareTo((BigDecimal) stored));
  }

  @Test
  void unweightedRowsStillCountAsOne() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.getFacts().add(new HFact("amount", AggregationMethod.SUM));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaNumber("amount"));
    crosstab.pivotRow(rowMeta, new Object[] {10.0});
    crosstab.pivotRow(rowMeta, new Object[] {20.0});

    assertEquals(30.0, crosstab.getPivotMapList().get(0).get(List.of("-")));
    assertEquals(2L, crosstab.getCountMapList().get(0).get(List.of("-")));
  }

  @Test
  void nullWeightSkipsTheRow() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.getFacts().add(weightedAverage());

    IRowMeta rowMeta = weightedRowMeta();
    crosstab.pivotRow(rowMeta, new Object[] {10.0, null});

    assertNull(crosstab.getPivotMapList().get(0).get(List.of("-")));
    assertNull(crosstab.getCountMapList().get(0).get(List.of("-")));
  }

  @Test
  void formatAverageKeepsFractionForIntegerAndBigNumber() throws Exception {
    IValueMeta integer = new ValueMetaInteger("qty");
    integer.setConversionMask("0.0");
    assertEquals("2.5", HBaseAggregatingComponent.formatAverage(integer, 10L, 4L));

    IValueMeta big = new ValueMetaBigNumber("amount");
    big.setConversionMask("0.00");
    assertEquals(
        "2.50", HBaseAggregatingComponent.formatAverage(big, new BigDecimal("10.00"), 4L));
  }

  @Test
  void factCopyAndRuntimeStateKeepTheWeightColumn() throws Exception {
    HFact fact = weightedAverage();
    assertEquals("amount_n", new HFact(fact).getWeightColumnName());

    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.getFacts().add(fact);
    IRowMeta rowMeta = weightedRowMeta();
    crosstab.pivotRow(rowMeta, new Object[] {10.0, 2L});

    HAggregatingRuntimeState state = HAggregatingRuntimeState.capture(crosstab);
    HCrosstabComponent restored = new HCrosstabComponent();
    restored.getFacts().add(new HFact(fact));
    state.restoreOnto(restored);
    assertEquals(List.of(0), restored.getFactIndexes());
    assertEquals(List.of(1), restored.getWeightIndexes());
    assertEquals(10.0, restored.getPivotMapList().get(0).get(List.of("-")));
    assertEquals(2L, restored.getCountMapList().get(0).get(List.of("-")));
  }

  private static HFact weightedAverage() {
    HFact fact = new HFact("amount", AggregationMethod.AVERAGE);
    fact.setWeightColumnName("amount_n");
    return fact;
  }

  private static IRowMeta weightedRowMeta() {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaNumber("amount"));
    rowMeta.addValueMeta(new ValueMetaInteger("amount_n"));
    return rowMeta;
  }
}
