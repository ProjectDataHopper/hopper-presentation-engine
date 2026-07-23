package org.hopper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HGeometryTest {

  @Test
  void containsPointInsideAndOutside() {
    HGeometry g = new HGeometry(10, 20, 100, 50);
    assertTrue(g.contains(10, 20));
    assertTrue(g.contains(50, 40));
    assertTrue(g.contains(109, 69));
    assertFalse(g.contains(9, 20));
    assertFalse(g.contains(10, 70));
  }

  @Test
  void maxSurfaceExpandsBoundingBox() {
    HGeometry a = new HGeometry(10, 10, 20, 20);
    HGeometry b = new HGeometry(5, 15, 40, 30);
    a.maxSurface(b);
    assertEquals(5, a.getX());
    assertEquals(10, a.getY());
    // width/height stored as max of right/bottom edges (existing behavior)
    assertEquals(45, a.getWidth());
    assertEquals(45, a.getHeight());
  }

  @Test
  void equalsAndCopy() {
    HGeometry a = new HGeometry(1, 2, 3, 4);
    HGeometry b = new HGeometry(a);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }
}
