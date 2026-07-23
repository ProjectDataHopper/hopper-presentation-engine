package org.hopper.presentation.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayoutBuilder;

class HPageTest {

  @Test
  void independentComponentsRetainListOrder() throws HException {
    HPage page = new HPage();
    // All page-relative (no inter-component layout refs) — must keep list order, not sort by name
    for (String name : List.of("Zebra", "Alpha", "Middle")) {
      HComponent component = new HComponent(name, null);
      component.setLayout(new HLayoutBuilder().top().left().build());
      page.getComponents().add(component);
    }

    List<String> sortedNames =
        page.getSortedComponents().stream().map(HComponent::getName).collect(Collectors.toList());
    assertEquals(List.of("Zebra", "Alpha", "Middle"), sortedNames);
  }

  @Test
  void testGetSortedComponents() throws HException {
    HPage page = new HPage();

    {
      HComponent component = new HComponent("D", null);
      component.setLayout(new HLayoutBuilder().below("C", 0).build());
      page.getComponents().add(component);
    }

    {
      HComponent component = new HComponent("C", null);
      component.setLayout(new HLayoutBuilder().below("B", 0).build());
      page.getComponents().add(component);
    }

    {
      HComponent component = new HComponent("B", null);
      component.setLayout(new HLayoutBuilder().below("A", 0).build());
      page.getComponents().add(component);
    }

    {
      HComponent component = new HComponent("A", null);
      component.setLayout(new HLayoutBuilder().top().left().build());
      page.getComponents().add(component);
    }

    {
      HComponent component = new HComponent("C2", null);
      component.setLayout(new HLayoutBuilder().beside("C1", 5).build());
      page.getComponents().add(component);
    }

    {
      HComponent component = new HComponent("C1", null);
      component.setLayout(new HLayoutBuilder().beside("C", 5).build());
      page.getComponents().add(component);
    }

    {
      HComponent component = new HComponent("E", null);
      component.setLayout(new HLayoutBuilder().top().right().build());
      page.getComponents().add(component);
    }

    List<HComponent> sortedComponents = page.getSortedComponents();
    verifySortedList(sortedComponents);

    final Random random = new Random(42);
    for (int i = 0; i < 1000; i++) {
      Collections.shuffle(page.getComponents(), random);
      verifySortedList(page.getSortedComponents());
    }
  }

  private void verifySortedList(List<HComponent> sortedComponents) {
    // All components present once
    assertEquals(7, sortedComponents.size(), "expected all components");
    Set<String> names =
        sortedComponents.stream().map(HComponent::getName).collect(Collectors.toSet());
    assertEquals(Set.of("A", "B", "C", "C1", "C2", "D", "E"), names);

    // Layout dependencies must appear before dependents
    Map<String, Integer> pos = new HashMap<>();
    for (int i = 0; i < sortedComponents.size(); i++) {
      pos.put(sortedComponents.get(i).getName(), i);
    }
    assertTrue(pos.get("A") < pos.get("B"), "A before B");
    assertTrue(pos.get("B") < pos.get("C"), "B before C");
    assertTrue(pos.get("C") < pos.get("D"), "C before D");
    assertTrue(pos.get("C") < pos.get("C1"), "C before C1");
    assertTrue(pos.get("C1") < pos.get("C2"), "C1 before C2");
  }
}
