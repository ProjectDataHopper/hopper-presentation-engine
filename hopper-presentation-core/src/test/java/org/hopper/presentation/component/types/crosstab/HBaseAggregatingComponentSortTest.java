package org.hopper.presentation.component.types.crosstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HBaseAggregatingComponentSortTest {

  @Test
  void numericStringsSortNumerically() {
    assertTrue(HBaseAggregatingComponent.compareNullableStrings("2", "10") < 0);
    assertTrue(HBaseAggregatingComponent.compareNullableStrings("10", "2") > 0);
    assertEquals(0, HBaseAggregatingComponent.compareNullableStrings("10", "10.0"));
    assertTrue(HBaseAggregatingComponent.compareNullableStrings("0", "1") < 0);
  }

  @Test
  void nonNumericStringsStayLexicographic() {
    assertTrue(HBaseAggregatingComponent.compareNullableStrings("Dummy", "Generator") < 0);
    assertTrue(HBaseAggregatingComponent.compareNullableStrings("a", "b") < 0);
  }

  @Test
  void nullsSortFirst() {
    assertTrue(HBaseAggregatingComponent.compareNullableStrings(null, "1") < 0);
    assertEquals(0, HBaseAggregatingComponent.compareNullableStrings(null, null));
  }
}
