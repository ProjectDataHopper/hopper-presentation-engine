package org.hopper.metadata.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.core.HAttachment;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.metadata.codec.HMetadataCodec;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HConnectorPluginType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionLocationOption;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.context.PresentationRenderContext;

/**
 * Structural and semantic validation for presentations and connectors. Stable issue codes support
 * agent repair loops.
 */
public class HMetadataValidator {

  public static final String PARSE_ERROR = "PARSE_ERROR";
  public static final String UNKNOWN_PLUGIN = "UNKNOWN_PLUGIN";
  public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
  public static final String ATTACHMENT_MISSING = "ATTACHMENT_MISSING";
  public static final String ATTACHMENT_CYCLE = "ATTACHMENT_CYCLE";
  public static final String LAYOUT_INVALID = "LAYOUT_INVALID";
  public static final String CONNECTOR_MISSING = "CONNECTOR_MISSING";
  public static final String INTERACTION_CATEGORY = "INTERACTION_CATEGORY";
  public static final String EMPTY_NAME = "EMPTY_NAME";
  public static final String SMOKE_LAYOUT = "SMOKE_LAYOUT";
  public static final String MISSING_PLUGIN_BODY = "MISSING_PLUGIN_BODY";

  public ValidationReport validatePresentationJson(String json, ValidateOptions options) {
    ValidateOptions opts = options != null ? options : ValidateOptions.builder().build();
    ValidationReport report = new ValidationReport();
    HPresentation presentation;
    try {
      presentation = HMetadataCodec.parsePresentation(json);
    } catch (HException e) {
      report.error(PARSE_ERROR, "$", e.getMessage());
      return report;
    }
    validatePresentation(presentation, opts, report);
    return report;
  }

  public ValidationReport validatePresentation(HPresentation presentation, ValidateOptions options) {
    ValidateOptions opts = options != null ? options : ValidateOptions.builder().build();
    ValidationReport report = new ValidationReport();
    validatePresentation(presentation, opts, report);
    return report;
  }

  public ValidationReport validateConnectorJson(String json, ValidateOptions options) {
    ValidateOptions opts = options != null ? options : ValidateOptions.builder().build();
    ValidationReport report = new ValidationReport();
    HConnector connector;
    try {
      connector = HMetadataCodec.parseConnector(json);
    } catch (HException e) {
      report.error(PARSE_ERROR, "$", e.getMessage());
      return report;
    }
    validateConnector(connector, opts, report);
    return report;
  }

  public ValidationReport validateConnector(HConnector connector, ValidateOptions options) {
    ValidateOptions opts = options != null ? options : ValidateOptions.builder().build();
    ValidationReport report = new ValidationReport();
    validateConnector(connector, opts, report);
    return report;
  }

  private void validatePresentation(
      HPresentation presentation, ValidateOptions opts, ValidationReport report) {
    if (presentation == null) {
      report.error(PARSE_ERROR, "$", "Presentation is null");
      return;
    }
    if (StringUtils.isBlank(presentation.getName())) {
      report.warning(EMPTY_NAME, "name", "Presentation name is empty");
    }

    ConnectorCatalog catalog = collectConnectorNames(opts.getMetadataProvider());

    List<HPage> pages = presentation.getPages();
    if (pages == null || pages.isEmpty()) {
      report.warning(EMPTY_NAME, "pages", "Presentation has no pages");
    } else {
      for (int p = 0; p < pages.size(); p++) {
        validatePage(pages.get(p), "pages[" + p + "]", catalog, opts, report);
      }
    }

    if (presentation.getHeader() != null) {
      validatePage(presentation.getHeader(), "header", catalog, opts, report);
    }
    if (presentation.getFooter() != null) {
      validatePage(presentation.getFooter(), "footer", catalog, opts, report);
    }

    validateInteractions(presentation, opts, report);

    if (opts.isIncludeSmokeLayout()) {
      smokeLayout(presentation, opts, report);
    }
  }

  private void validatePage(
      HPage page,
      String path,
      ConnectorCatalog catalog,
      ValidateOptions opts,
      ValidationReport report) {
    if (page == null) {
      return;
    }
    List<HComponent> components = page.getComponents();
    if (components == null) {
      return;
    }

    Map<String, HComponent> byName = new HashMap<>();
    for (int i = 0; i < components.size(); i++) {
      HComponent c = components.get(i);
      String cPath = path + ".components[" + i + "]";
      if (c == null) {
        report.error(MISSING_PLUGIN_BODY, cPath, "Null component entry");
        continue;
      }
      if (StringUtils.isBlank(c.getName())) {
        report.error(EMPTY_NAME, cPath + ".name", "Component name is empty");
      } else if (byName.containsKey(c.getName())) {
        report.error(
            DUPLICATE_NAME,
            cPath + ".name",
            "Duplicate component name '" + c.getName() + "' on page");
      } else {
        byName.put(c.getName(), c);
      }

      IHComponent plugin = c.getComponent();
      if (plugin == null) {
        report.error(MISSING_PLUGIN_BODY, cPath + ".component", "Missing component plugin body");
      } else {
        String pluginId = plugin.getPluginId();
        if (StringUtils.isBlank(pluginId)) {
          report.error(UNKNOWN_PLUGIN, cPath + ".component", "Missing pluginId");
        } else if (!pluginRegistered(HComponentPluginType.class, pluginId)) {
          report.error(
              UNKNOWN_PLUGIN,
              cPath + ".component.pluginId",
              "Unknown component plugin '" + pluginId + "'");
        }
        String source = plugin.getSourceConnectorName();
        if (StringUtils.isNotBlank(source)) {
          if (catalog.available && !catalog.names.contains(source)) {
            report.error(
                CONNECTOR_MISSING,
                cPath + ".component.sourceConnectorName",
                "Source connector '" + source + "' is not in the metadata catalog");
          } else if (!catalog.available) {
            report.warning(
                CONNECTOR_MISSING,
                cPath + ".component.sourceConnectorName",
                "Source connector '"
                    + source
                    + "' could not be verified (no connector catalog in options)");
          }
        }
      }

      HLayout layout = c.getLayout();
      if (layout != null) {
        try {
          layout.validate(c);
        } catch (HException e) {
          report.error(LAYOUT_INVALID, cPath + ".layout", e.getMessage());
        }
        checkAttachmentRef(layout.getLeft(), byName, cPath + ".layout.left", report);
        checkAttachmentRef(layout.getRight(), byName, cPath + ".layout.right", report);
        checkAttachmentRef(layout.getTop(), byName, cPath + ".layout.top", report);
        checkAttachmentRef(layout.getBottom(), byName, cPath + ".layout.bottom", report);
      }
    }

    detectAttachmentCycles(components, path, report);
  }

  private void checkAttachmentRef(
      HAttachment attachment,
      Map<String, HComponent> byName,
      String path,
      ValidationReport report) {
    if (attachment == null) {
      return;
    }
    String ref = attachment.getComponentName();
    if (StringUtils.isNotEmpty(ref) && !byName.containsKey(ref)) {
      report.error(
          ATTACHMENT_MISSING,
          path + ".componentName",
          "Attachment references unknown component '" + ref + "'");
    }
  }

  private void detectAttachmentCycles(
      List<HComponent> components, String pagePath, ValidationReport report) {
    Map<String, Set<String>> edges = new HashMap<>();
    for (HComponent c : components) {
      if (c == null || StringUtils.isBlank(c.getName()) || c.getLayout() == null) {
        continue;
      }
      Set<String> deps = c.getLayout().getReferencedLayoutComponentNames();
      edges.put(c.getName(), deps != null ? deps : Set.of());
    }

    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();
    for (String name : edges.keySet()) {
      if (cycleFrom(name, edges, visiting, visited, new ArrayList<>(), pagePath, report)) {
        return;
      }
    }
  }

  private boolean cycleFrom(
      String node,
      Map<String, Set<String>> edges,
      Set<String> visiting,
      Set<String> visited,
      List<String> stack,
      String pagePath,
      ValidationReport report) {
    if (visited.contains(node)) {
      return false;
    }
    if (visiting.contains(node)) {
      report.error(
          ATTACHMENT_CYCLE,
          pagePath + ".components",
          "Attachment cycle involving '" + node + "' (path " + String.join(" -> ", stack) + " -> "
              + node + ")");
      return true;
    }
    visiting.add(node);
    stack.add(node);
    for (String next : edges.getOrDefault(node, Set.of())) {
      if (cycleFrom(next, edges, visiting, visited, stack, pagePath, report)) {
        return true;
      }
    }
    stack.remove(stack.size() - 1);
    visiting.remove(node);
    visited.add(node);
    return false;
  }

  private void validateInteractions(
      HPresentation presentation, ValidateOptions opts, ValidationReport report) {
    List<HInteraction> interactions = presentation.getInteractions();
    if (interactions == null) {
      return;
    }
    Map<String, HComponent> all = indexAllComponents(presentation);
    for (int i = 0; i < interactions.size(); i++) {
      HInteraction interaction = interactions.get(i);
      String path = "interactions[" + i + "]";
      if (interaction == null || interaction.getLocation() == null) {
        continue;
      }
      HInteractionLocation loc = interaction.getLocation();
      if (StringUtils.isNotBlank(loc.getComponentName())
          && !all.containsKey(loc.getComponentName())) {
        report.error(
            ATTACHMENT_MISSING,
            path + ".location.componentName",
            "Interaction targets unknown component '" + loc.getComponentName() + "'");
        continue;
      }
      if (StringUtils.isBlank(loc.getItemCategory())) {
        continue;
      }
      // Whole-component is always valid
      if (DrawnItem.Category.ComponentArea.name().equals(loc.getItemCategory())) {
        continue;
      }
      HComponent target = all.get(loc.getComponentName());
      if (target == null || target.getComponent() == null) {
        continue;
      }
      Set<String> allowed = allowedCategories(target.getComponent());
      if (!allowed.contains(loc.getItemCategory())) {
        String msg =
            "Interaction category '"
                + loc.getItemCategory()
                + "' is not declared by plugin "
                + target.getComponent().getPluginId()
                + " (allowed: "
                + allowed
                + ")";
        if (opts.isStrictInteractions()) {
          report.error(INTERACTION_CATEGORY, path + ".location.itemCategory", msg);
        } else {
          report.warning(INTERACTION_CATEGORY, path + ".location.itemCategory", msg);
        }
      }
    }
  }

  private Set<String> allowedCategories(IHComponent plugin) {
    Set<String> set = new HashSet<>();
    set.add(DrawnItem.Category.ComponentArea.name());
    List<HInteractionLocationOption> opts = plugin.getPossibleInteractionLocations();
    if (opts != null) {
      for (HInteractionLocationOption o : opts) {
        if (o != null && StringUtils.isNotBlank(o.getItemCategory())) {
          set.add(o.getItemCategory());
        }
      }
    }
    return set;
  }

  private Map<String, HComponent> indexAllComponents(HPresentation presentation) {
    Map<String, HComponent> map = new HashMap<>();
    if (presentation.getPages() != null) {
      for (HPage page : presentation.getPages()) {
        if (page.getComponents() != null) {
          for (HComponent c : page.getComponents()) {
            if (c != null && StringUtils.isNotBlank(c.getName())) {
              map.put(c.getName(), c);
            }
          }
        }
      }
    }
    return map;
  }

  private void smokeLayout(
      HPresentation presentation, ValidateOptions opts, ValidationReport report) {
    IHopMetadataProvider provider =
        opts.getMetadataProvider() != null
            ? opts.getMetadataProvider()
            : new MemoryMetadataProvider();
    try {
      PresentationRenderContext ctx = new PresentationRenderContext(presentation, provider);
      HLayoutResults results =
          presentation.doLayout(
              new LoggingObject("metadata-validate"),
              ctx,
              provider,
              opts.getParameters() != null ? opts.getParameters() : Collections.emptyList());
      if (results == null || results.getRenderPages() == null || results.getRenderPages().isEmpty()) {
        report.warning(SMOKE_LAYOUT, "$", "Smoke layout produced no render pages");
      }
    } catch (Exception e) {
      report.error(SMOKE_LAYOUT, "$", "Smoke layout failed: " + e.getMessage());
    }
  }

  private void validateConnector(
      HConnector connector, ValidateOptions opts, ValidationReport report) {
    if (connector == null) {
      report.error(PARSE_ERROR, "$", "Connector is null");
      return;
    }
    if (StringUtils.isBlank(connector.getName())) {
      report.warning(EMPTY_NAME, "name", "Connector name is empty");
    }
    IHConnector plugin = connector.getConnector();
    if (plugin == null) {
      report.error(MISSING_PLUGIN_BODY, "connector", "Missing connector plugin body");
      return;
    }
    String pluginId = plugin.getPluginId();
    if (StringUtils.isBlank(pluginId)) {
      report.error(UNKNOWN_PLUGIN, "connector.pluginId", "Missing pluginId");
    } else if (!pluginRegistered(HConnectorPluginType.class, pluginId)) {
      report.error(
          UNKNOWN_PLUGIN,
          "connector.pluginId",
          "Unknown connector plugin '" + pluginId + "'");
    }
  }

  private static final class ConnectorCatalog {
    final boolean available;
    final Set<String> names;

    ConnectorCatalog(boolean available, Set<String> names) {
      this.available = available;
      this.names = names;
    }
  }

  private ConnectorCatalog collectConnectorNames(IHopMetadataProvider provider) {
    if (provider == null) {
      return new ConnectorCatalog(false, Set.of());
    }
    Set<String> names = new HashSet<>();
    try {
      for (String n : provider.getSerializer(HConnector.class).listObjectNames()) {
        if (StringUtils.isNotBlank(n)) {
          names.add(n);
        }
      }
      return new ConnectorCatalog(true, names);
    } catch (Exception e) {
      return new ConnectorCatalog(false, Set.of());
    }
  }

  private static boolean pluginRegistered(
      Class<? extends org.apache.hop.core.plugins.IPluginType> type, String id) {
    try {
      IPlugin plugin = PluginRegistry.getInstance().findPluginWithId(type, id);
      return plugin != null;
    } catch (Exception e) {
      return false;
    }
  }
}
