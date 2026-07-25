package org.hopper.presentation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.page.HPage;

class HLayoutPageLimitTest {

  @BeforeEach
  void setUp() {
    HLayoutPageLimitSettings.setForTests(3);
  }

  @AfterEach
  void tearDown() {
    HLayoutPageLimitSettings.resetToDefaults();
  }

  @Test
  void addNewPage_stopsAtMaxAndMarksTruncated() {
    HLayoutResults results = new HLayoutResults(null);
    results.setMaxRenderPages(3);
    HPage page = HPage.getA4(false);

    HRenderPage p1 = results.addNewPage(page, null);
    HRenderPage p2 = results.addNewPage(page, p1);
    HRenderPage p3 = results.addNewPage(page, p2);
    assertEquals(3, results.getRenderPages().size());
    assertFalse(results.isPagesTruncated());
    assertTrue(results.isAtRenderPageLimit());

    HRenderPage p4 = results.addNewPage(page, p3);
    assertEquals(3, results.getRenderPages().size());
    assertTrue(results.isPagesTruncated());
    // Returned last page instead of growing
    assertEquals(p3, p4);
  }

  @Test
  void underLimit_notTruncated() {
    HLayoutResults results = new HLayoutResults(null);
    results.setMaxRenderPages(10);
    HPage page = HPage.getA4(false);
    results.addNewPage(page, null);
    results.addNewPage(page, results.getRenderPages().get(0));
    assertEquals(2, results.getRenderPages().size());
    assertFalse(results.isPagesTruncated());
    assertFalse(results.isAtRenderPageLimit());
  }

  @Test
  void settingsClamp() {
    HLayoutPageLimitSettings.setForTests(0);
    assertEquals(1, HLayoutPageLimitSettings.getMaxRenderPages());
    HLayoutPageLimitSettings.setForTests(5000);
    assertEquals(1000, HLayoutPageLimitSettings.getMaxRenderPages());
  }
}
