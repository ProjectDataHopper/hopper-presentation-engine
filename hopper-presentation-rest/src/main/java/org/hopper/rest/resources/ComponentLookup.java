package org.hopper.rest.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.types.composite.HCompositeComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.page.HPage;

/**
 * Resolve presentation components by real metadata name or by synthetic drawn-item names produced
 * by Group / Composite layout:
 *
 * <pre>
 *   Group-group#1:Composite1
 *   Group-group#1:Composite1-child(Label1)
 * </pre>
 *
 * Returns the <b>template</b> {@link HComponent} in the presentation metadata (not the runtime
 * copy), plus enough parent context to replace it on save and a full {@link Found#lineage} for
 * breadcrumb navigation.
 */
public final class ComponentLookup {

  private ComponentLookup() {}

  public static final class Found {
    public final HComponent component;
    /** Logical body page index, or -1 for header/footer. */
    public final int logicalPageNumber;
    public final String pageRole;
    /** Page (or header/footer page) that owns the top-level tree. */
    public final HPage page;
    /**
     * Parent {@link HComponent} when nested; null if {@link #component} is top-level on the
     * page.
     */
    public final HComponent parentComponent;
    /** True when this component is a group's {@code groupComponent} template. */
    public final boolean groupTemplate;
    /** Index in composite children when nested under a composite; -1 otherwise. */
    public final int childIndex;
    /**
     * Top-level page component down to {@link #component} (inclusive). Empty only if construct is
     * incomplete; normally at least one entry.
     */
    public final List<HComponent> lineage;

    public Found(
        HComponent component,
        int logicalPageNumber,
        String pageRole,
        HPage page,
        HComponent parentComponent,
        boolean groupTemplate,
        int childIndex,
        List<HComponent> lineage) {
      this.component = component;
      this.logicalPageNumber = logicalPageNumber;
      this.pageRole = pageRole;
      this.page = page;
      this.parentComponent = parentComponent;
      this.groupTemplate = groupTemplate;
      this.childIndex = childIndex;
      this.lineage =
          lineage == null || lineage.isEmpty()
              ? List.of(component)
              : Collections.unmodifiableList(new ArrayList<>(lineage));
    }
  }

  /**
   * Find a component by drawn or metadata name on the preferred page, then header/footer, then all
   * body pages.
   */
  public static Found find(
      HPresentation presentation, HPage preferredPage, String componentName)
      throws HException {
    if (StringUtils.isBlank(componentName)) {
      return null;
    }
    if (preferredPage != null) {
      Found f =
          findOnPage(
              preferredPage,
              componentName,
              indexOf(presentation, preferredPage),
              roleOf(preferredPage, presentation));
      if (f != null) {
        return f;
      }
    }
    if (presentation == null) {
      return null;
    }
    if (presentation.getHeader() != null) {
      Found f = findOnPage(presentation.getHeader(), componentName, -1, "header");
      if (f != null) {
        return f;
      }
    }
    if (presentation.getFooter() != null) {
      Found f = findOnPage(presentation.getFooter(), componentName, -1, "footer");
      if (f != null) {
        return f;
      }
    }
    List<HPage> pages = presentation.getPages();
    if (pages != null) {
      for (int i = 0; i < pages.size(); i++) {
        Found f = findOnPage(pages.get(i), componentName, i, "page");
        if (f != null) {
          return f;
        }
      }
    }
    return null;
  }

  public static Found findOnPage(
      HPage page, String componentName, int logicalPageNumber, String pageRole)
      throws HException {
    if (page == null || page.getComponents() == null) {
      return null;
    }
    for (HComponent top : page.getComponents()) {
      if (top == null) {
        continue;
      }
      List<HComponent> pathToTop = List.of(top);
      // Exact top-level match
      if (componentName.equalsIgnoreCase(top.getName())) {
        return makeFound(top, logicalPageNumber, pageRole, page, null, false, -1, pathToTop);
      }
      // Nested template by exact name (e.g. "Label1" inside composite)
      Found deep =
          findNestedByTemplateName(top, pathToTop, componentName, logicalPageNumber, pageRole, page);
      if (deep != null) {
        return deep;
      }
      // Synthetic drawn name under this top-level root
      Found drawn =
          matchDrawn(
              top, top.getName(), pathToTop, componentName, logicalPageNumber, pageRole, page);
      if (drawn != null) {
        return drawn;
      }
    }
    return null;
  }

  private static Found makeFound(
      HComponent component,
      int logicalPageNumber,
      String pageRole,
      HPage page,
      HComponent parentComponent,
      boolean groupTemplate,
      int childIndex,
      List<HComponent> lineage) {
    return new Found(
        component,
        logicalPageNumber,
        pageRole,
        page,
        parentComponent,
        groupTemplate,
        childIndex,
        lineage);
  }

  private static List<HComponent> extend(List<HComponent> path, HComponent next) {
    List<HComponent> out = new ArrayList<>(path.size() + 1);
    out.addAll(path);
    out.add(next);
    return out;
  }

  /**
   * Search nested templates by their metadata names (unique enough for simple presentations).
   *
   * @param node current container to search inside
   * @param pathToNode lineage from top-level page component through {@code node} (inclusive)
   */
  private static Found findNestedByTemplateName(
      HComponent node,
      List<HComponent> pathToNode,
      String name,
      int logicalPageNumber,
      String pageRole,
      HPage page) {
    if (node == null || pathToNode == null || pathToNode.isEmpty()) {
      return null;
    }
    IHComponent impl = node.getComponent();
    if (impl instanceof HGroupComponent group) {
      HComponent template = group.getGroupComponent();
      if (template != null) {
        List<HComponent> pathToTemplate = extend(pathToNode, template);
        if (name.equalsIgnoreCase(template.getName())) {
          return makeFound(
              template, logicalPageNumber, pageRole, page, node, true, -1, pathToTemplate);
        }
        Found f =
            findNestedByTemplateName(
                template, pathToTemplate, name, logicalPageNumber, pageRole, page);
        if (f != null) {
          return f;
        }
      }
    }
    if (impl instanceof HCompositeComponent composite && composite.getChildren() != null) {
      List<HComponent> children = composite.getChildren();
      for (int i = 0; i < children.size(); i++) {
        HComponent child = children.get(i);
        if (child == null) {
          continue;
        }
        List<HComponent> pathToChild = extend(pathToNode, child);
        if (name.equalsIgnoreCase(child.getName())) {
          return makeFound(child, logicalPageNumber, pageRole, page, node, false, i, pathToChild);
        }
        Found f =
            findNestedByTemplateName(child, pathToChild, name, logicalPageNumber, pageRole, page);
        if (f != null) {
          return f;
        }
      }
    }
    return null;
  }

  /**
   * Match synthetic runtime names produced by Group/Composite processSourceData.
   *
   * @param meta the template component in metadata
   * @param runtimeName the name this template would have at this nesting level when drawn
   * @param pathToMeta lineage from top-level through {@code meta} (inclusive)
   */
  private static Found matchDrawn(
      HComponent meta,
      String runtimeName,
      List<HComponent> pathToMeta,
      String drawnName,
      int logicalPageNumber,
      String pageRole,
      HPage page) {
    if (meta == null || runtimeName == null || pathToMeta == null || pathToMeta.isEmpty()) {
      return null;
    }
    HComponent parent =
        pathToMeta.size() > 1 ? pathToMeta.get(pathToMeta.size() - 2) : null;
    boolean groupTemplate = false;
    int childIndex = -1;
    if (parent != null && parent.getComponent() instanceof HGroupComponent) {
      groupTemplate = true;
    } else if (parent != null && parent.getComponent() instanceof HCompositeComponent comp
        && comp.getChildren() != null) {
      List<HComponent> children = comp.getChildren();
      for (int i = 0; i < children.size(); i++) {
        if (children.get(i) == meta
            || (meta.getName() != null
                && meta.getName().equalsIgnoreCase(children.get(i).getName()))) {
          childIndex = i;
          break;
        }
      }
    }

    if (runtimeName.equals(drawnName)) {
      return makeFound(
          meta,
          logicalPageNumber,
          pageRole,
          page,
          parent,
          groupTemplate,
          childIndex,
          pathToMeta);
    }
    if (!drawnName.startsWith(runtimeName)) {
      return null;
    }

    IHComponent impl = meta.getComponent();
    if (impl instanceof HGroupComponent group && group.getGroupComponent() != null) {
      HComponent template = group.getGroupComponent();
      Pattern p =
          Pattern.compile(
              "^"
                  + Pattern.quote(runtimeName)
                  + "-group#(\\d+):"
                  + Pattern.quote(template.getName())
                  + "(.*)$");
      Matcher m = p.matcher(drawnName);
      if (m.matches()) {
        String rowRuntime = runtimeName + "-group#" + m.group(1) + ":" + template.getName();
        List<HComponent> pathToTemplate = extend(pathToMeta, template);
        return matchDrawn(
            template, rowRuntime, pathToTemplate, drawnName, logicalPageNumber, pageRole, page);
      }
    }

    if (impl instanceof HCompositeComponent composite && composite.getChildren() != null) {
      List<HComponent> children = composite.getChildren();
      for (int i = 0; i < children.size(); i++) {
        HComponent child = children.get(i);
        if (child == null || child.getName() == null) {
          continue;
        }
        String childRuntime = runtimeName + "-child(" + child.getName() + ")";
        if (drawnName.equals(childRuntime) || drawnName.startsWith(childRuntime)) {
          List<HComponent> pathToChild = extend(pathToMeta, child);
          Found f =
              matchDrawn(
                  child, childRuntime, pathToChild, drawnName, logicalPageNumber, pageRole, page);
          if (f != null) {
            return f;
          }
        }
      }
    }
    return null;
  }

  /**
   * Replace {@code old} with {@code replacement} in the presentation structure described by {@code
   * found}.
   */
  public static void replace(Found found, HComponent replacement) throws HException {
    if (found == null || replacement == null) {
      throw new HException("Cannot replace null component");
    }
    if (found.parentComponent == null) {
      // Top-level on page
      List<HComponent> list = found.page.getComponents();
      int idx = list.indexOf(found.component);
      if (idx < 0) {
        // identity may differ after load — match by name
        for (int i = 0; i < list.size(); i++) {
          if (found.component.getName() != null
              && found.component.getName().equalsIgnoreCase(list.get(i).getName())) {
            idx = i;
            break;
          }
        }
      }
      if (idx < 0) {
        throw new HException(
            "Unable to find top-level component '" + found.component.getName() + "' to replace");
      }
      list.set(idx, replacement);
      return;
    }

    IHComponent parentImpl = found.parentComponent.getComponent();
    if (found.groupTemplate && parentImpl instanceof HGroupComponent group) {
      group.setGroupComponent(replacement);
      return;
    }
    if (!found.groupTemplate && parentImpl instanceof HCompositeComponent composite) {
      List<HComponent> children = composite.getChildren();
      if (found.childIndex >= 0 && found.childIndex < children.size()) {
        children.set(found.childIndex, replacement);
        return;
      }
      // fallback by name
      for (int i = 0; i < children.size(); i++) {
        if (found.component.getName() != null
            && found.component.getName().equalsIgnoreCase(children.get(i).getName())) {
          children.set(i, replacement);
          return;
        }
      }
      throw new HException(
          "Unable to find composite child '" + found.component.getName() + "' to replace");
    }
    throw new HException(
        "Unsupported parent type for nested component replace: "
            + (parentImpl != null ? parentImpl.getClass().getSimpleName() : "null"));
  }

  private static int indexOf(HPresentation presentation, HPage page) {
    if (presentation == null || page == null || presentation.getPages() == null) {
      return -1;
    }
    for (int i = 0; i < presentation.getPages().size(); i++) {
      if (presentation.getPages().get(i) == page) {
        return i;
      }
    }
    return presentation.getPages().isEmpty() ? -1 : 0;
  }

  private static String roleOf(HPage page, HPresentation presentation) {
    if (page == null) {
      return "page";
    }
    if (page.isHeader() || (presentation != null && presentation.getHeader() == page)) {
      return "header";
    }
    if (page.isFooter() || (presentation != null && presentation.getFooter() == page)) {
      return "footer";
    }
    return "page";
  }
}
