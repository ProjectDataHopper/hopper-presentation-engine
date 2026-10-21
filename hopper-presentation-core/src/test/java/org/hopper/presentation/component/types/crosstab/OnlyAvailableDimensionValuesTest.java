package org.hopper.presentation.component.types.crosstab;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HDimension;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFact;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The default crosstab repeats every vertical pairing (2025-03 stays when March exists in 2024).
 * Only available dimension values keeps pairings that have a fact.
 */
class OnlyAvailableDimensionValuesTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void cartesianGridRepeatsAMonthThatExistsForAnotherYear() throws Exception {
    HCrosstabComponent crosstab = yearMonthCrosstab(false);
    pivotYearMonth(crosstab);

    Set<List<String>> horizontal = new HashSet<>();
    Set<List<String>> vertical = new HashSet<>();
    crosstab.resolveAxisCombinations(horizontal, vertical);

    assertTrue(vertical.contains(List.of("2024", "3")));
    assertTrue(vertical.contains(List.of("2025", "3")));
    assertTrue(horizontal.contains(List.of("DELIVERED", "WEB")));
    assertTrue(horizontal.contains(List.of("DELIVERED", "STORE")));
  }

  @Test
  void onlyAvailableValuesOmitsCombinationsWithNoFacts() throws Exception {
    HCrosstabComponent crosstab = yearMonthCrosstab(true);
    pivotYearMonth(crosstab);

    Set<List<String>> horizontal = new HashSet<>();
    Set<List<String>> vertical = new HashSet<>();
    crosstab.resolveAxisCombinations(horizontal, vertical);

    assertTrue(vertical.contains(List.of("2024", "1")));
    assertTrue(vertical.contains(List.of("2024", "3")));
    assertTrue(vertical.contains(List.of("2025", "1")));
    assertFalse(vertical.contains(List.of("2025", "3")));
    assertTrue(horizontal.contains(List.of("DELIVERED", "WEB")));
    assertTrue(horizontal.contains(List.of("CANCELLED", "STORE")));
    assertFalse(horizontal.contains(List.of("DELIVERED", "STORE")));
    assertFalse(horizontal.contains(List.of("CANCELLED", "WEB")));
  }

  @Test
  void countFactStillMarksTheRowAsAvailable() throws Exception {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.setOnlyAvailableDimensionValues(true);
    crosstab.getVerticalDimensions().add(new HDimension("year"));
    crosstab.getVerticalDimensions().add(new HDimension("month"));
    crosstab.getHorizontalDimensions().add(new HDimension("status"));
    crosstab.getFacts().add(new HFact("qty", AggregationMethod.COUNT));

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("year"));
    rowMeta.addValueMeta(new ValueMetaString("month"));
    rowMeta.addValueMeta(new ValueMetaString("status"));
    rowMeta.addValueMeta(new ValueMetaInteger("qty"));
    crosstab.pivotRow(rowMeta, new Object[] {"2025", "1", "NEW", 4L});
    crosstab.pivotRow(rowMeta, new Object[] {"2024", "3", "NEW", 1L});

    Set<List<String>> horizontal = new HashSet<>();
    Set<List<String>> vertical = new HashSet<>();
    crosstab.resolveAxisCombinations(horizontal, vertical);

    assertTrue(vertical.contains(List.of("2025", "1")));
    assertTrue(vertical.contains(List.of("2024", "3")));
    assertFalse(vertical.contains(List.of("2025", "3")));
    assertTrue(horizontal.contains(List.of("NEW")));
  }

  @Test
  void cloneKeepsTheOption() {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    assertFalse(crosstab.isOnlyAvailableDimensionValues());
    crosstab.setOnlyAvailableDimensionValues(true);
    assertTrue(crosstab.clone().isOnlyAvailableDimensionValues());
  }

  private static HCrosstabComponent yearMonthCrosstab(boolean onlyAvailable) {
    HCrosstabComponent crosstab = new HCrosstabComponent();
    crosstab.setOnlyAvailableDimensionValues(onlyAvailable);
    crosstab.getVerticalDimensions().add(new HDimension("year"));
    crosstab.getVerticalDimensions().add(new HDimension("month"));
    crosstab.getHorizontalDimensions().add(new HDimension("status"));
    crosstab.getHorizontalDimensions().add(new HDimension("channel"));
    crosstab.getFacts().add(new HFact("amount", AggregationMethod.SUM));
    return crosstab;
  }

  /** 2024 has March; 2025 does not. Channel WEB never pairs with CANCELLED. */
  private static void pivotYearMonth(HCrosstabComponent crosstab) throws Exception {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("year"));
    rowMeta.addValueMeta(new ValueMetaString("month"));
    rowMeta.addValueMeta(new ValueMetaString("status"));
    rowMeta.addValueMeta(new ValueMetaString("channel"));
    rowMeta.addValueMeta(new ValueMetaNumber("amount"));
    crosstab.pivotRow(rowMeta, new Object[] {"2024", "1", "DELIVERED", "WEB", 10.0});
    crosstab.pivotRow(rowMeta, new Object[] {"2024", "3", "DELIVERED", "WEB", 5.0});
    crosstab.pivotRow(rowMeta, new Object[] {"2024", "1", "CANCELLED", "STORE", 2.0});
    crosstab.pivotRow(rowMeta, new Object[] {"2025", "1", "DELIVERED", "WEB", 1.0});
  }
}
