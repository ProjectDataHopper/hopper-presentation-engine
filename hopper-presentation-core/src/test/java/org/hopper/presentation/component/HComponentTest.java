package org.hopper.presentation.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.hopper.core.exception.HException;
import org.hopper.presentation.layout.HLayoutBuilder;

class HComponentTest {

  @Test
  void testGetDependentComponents() throws HException {
    Map<String, HComponent> components = new HashMap<>();

    HComponent a = new HComponent("A", null);
    a.setLayout(new HLayoutBuilder().top().left().build());
    components.put(a.getName(), a);

    HComponent b = new HComponent("B", null);
    b.setLayout(new HLayoutBuilder().below("A", 5).build());
    components.put(b.getName(), b);

    HComponent c = new HComponent("C", null);
    c.setLayout(new HLayoutBuilder().below("B", 5).build());
    components.put(c.getName(), c);

    HComponent d = new HComponent("D", null);
    d.setLayout(new HLayoutBuilder().below("C", 5).build());
    components.put(d.getName(), d);

    HComponent e = new HComponent("E", null);
    e.setLayout(new HLayoutBuilder().beside("B", 5).build());
    components.put(e.getName(), e);

    Set<HComponent> aDependencies = a.getDependentComponents(components);
    assertTrue(aDependencies.isEmpty());

    Set<HComponent> bDependencies = b.getDependentComponents(components);
    assertEquals(1, bDependencies.size());
    assertTrue(bDependencies.contains(a));

    Set<HComponent> cDependencies = c.getDependentComponents(components);
    assertEquals(2, cDependencies.size());
    assertTrue(cDependencies.contains(a));
    assertTrue(cDependencies.contains(b));

    Set<HComponent> dDependencies = d.getDependentComponents(components);
    assertEquals(3, dDependencies.size());
    assertTrue(dDependencies.contains(a));
    assertTrue(dDependencies.contains(b));
    assertTrue(dDependencies.contains(c));

    Set<HComponent> eDependencies = e.getDependentComponents(components);
    assertEquals(2, eDependencies.size());
    assertTrue(eDependencies.contains(b));
    assertTrue(eDependencies.contains(a));
  }
}
