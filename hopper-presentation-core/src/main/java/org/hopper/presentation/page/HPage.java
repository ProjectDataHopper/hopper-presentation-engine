package org.hopper.presentation.page;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.layout.HLayout;

/** This represents one page in a presentation. */
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@JsonIgnoreProperties(value = {"pageNumber"})
public class HPage {
  /** An ID to uniquely identify a page while rendering. We don't serialize this. */
  @JsonIgnore private final String id;

  @HopMetadataProperty private int width;
  @HopMetadataProperty private int height;
  @HopMetadataProperty private int leftMargin;
  @HopMetadataProperty private int rightMargin;
  @HopMetadataProperty private int topMargin;
  @HopMetadataProperty private int bottomMargin;
  @HopMetadataProperty private List<HComponent> components;
  @HopMetadataProperty private boolean header;
  @HopMetadataProperty private boolean footer;

  public HPage() {
    this.id = UUID.randomUUID().toString();
    this.components = new ArrayList<>();
  }

  public HPage(
      int width, int height, int leftMargin, int rightMargin, int topMargin, int bottomMargin) {
    this();
    this.width = width;
    this.height = height;
    this.leftMargin = leftMargin;
    this.rightMargin = rightMargin;
    this.topMargin = topMargin;
    this.bottomMargin = bottomMargin;
  }

  public HPage(HPage p) {
    this();
    this.width = p.width;
    this.height = p.height;
    this.leftMargin = p.leftMargin;
    this.rightMargin = p.rightMargin;
    this.topMargin = p.topMargin;
    this.bottomMargin = p.bottomMargin;
    this.header = p.header;
    this.footer = p.footer;
    for (HComponent c : p.components) {
      this.components.add(new HComponent(c));
    }
  }

  public static HPage getA4(boolean portrait) {
    int width = 794;
    int height = 1123;
    if (portrait) {
      return new HPage(width, height, 25, 25, 25, 25);
    } else {
      return new HPage(height, width, 25, 25, 25, 25);
    }
  }

  public static HPage getHeaderFooter(boolean header, boolean portrait, int size) {
    int width = 794 - 25 - 25;
    int height = 1123 - 25 - 25;
    HPage page;
    if (portrait) {
      page = new HPage(width, size, 0, 0, 0, 0);
    } else {
      page = new HPage(height, size, 0, 0, 0, 0);
    }
    page.setHeader(header);
    page.setFooter(!header);
    return page;
  }

  @JsonIgnore
  public int getWidthBetweenMargins() {
    return width - leftMargin - rightMargin;
  }

  public HComponent findComponent(String componentName) throws HException {
    for (HComponent component : components) {
      if (component.getName().equalsIgnoreCase(componentName)) {
        return component;
      }
    }
    return null;
  }

  /**
   * Topological sort of components based on layout dependencies (referenced components first).
   *
   * <p>When components are not connected by layout attachments, their relative order is the
   * <b>index in {@link #components}</b> (stable list order). This drives layout and paint order:
   * later list entries are laid out and drawn later, so they appear on top when overlapping.
   */
  @JsonIgnore
  public List<HComponent> getSortedComponents() throws HException {
    if (components == null || components.isEmpty()) {
      return new ArrayList<>();
    }

    Map<String, HComponent> byName = new HashMap<>();
    Map<HComponent, Integer> originalIndex = new IdentityHashMap<>();
    List<HComponent> present = new ArrayList<>();
    for (int i = 0; i < components.size(); i++) {
      HComponent c = components.get(i);
      if (c == null) {
        continue;
      }
      present.add(c);
      originalIndex.put(c, i);
      if (c.getName() != null) {
        byName.put(c.getName(), c);
      }
    }

    // Direct edges: dependency → dependents (dep must be laid out before dependent)
    Map<HComponent, Set<HComponent>> dependents = new IdentityHashMap<>();
    Map<HComponent, Integer> inDegree = new IdentityHashMap<>();
    for (HComponent c : present) {
      dependents.put(c, new HashSet<>());
      inDegree.put(c, 0);
    }

    for (HComponent c : present) {
      HLayout layout = c.getLayout();
      if (layout == null) {
        continue;
      }
      for (String refName : layout.getReferencedLayoutComponentNames()) {
        HComponent dep = byName.get(refName);
        if (dep == null) {
          throw new HException(
              "Component " + c.getName() + " references " + refName + " which isn't known");
        }
        if (dep == c) {
          continue;
        }
        if (dependents.get(dep).add(c)) {
          inDegree.put(c, inDegree.get(c) + 1);
        }
      }
    }

    // Kahn: among ready nodes, preserve original list index
    PriorityQueue<HComponent> ready =
        new PriorityQueue<>(Comparator.comparingInt(originalIndex::get));
    for (HComponent c : present) {
      if (inDegree.get(c) == 0) {
        ready.add(c);
      }
    }

    List<HComponent> sorted = new ArrayList<>(present.size());
    while (!ready.isEmpty()) {
      HComponent c = ready.poll();
      sorted.add(c);
      for (HComponent dependent : dependents.get(c)) {
        int next = inDegree.get(dependent) - 1;
        inDegree.put(dependent, next);
        if (next == 0) {
          ready.add(dependent);
        }
      }
    }

    // Cycles or incomplete graph: append remaining in original order
    if (sorted.size() < present.size()) {
      Set<HComponent> seen = new HashSet<>(sorted);
      for (HComponent c : present) {
        if (!seen.contains(c)) {
          sorted.add(c);
        }
      }
    }
    return sorted;
  }
}
