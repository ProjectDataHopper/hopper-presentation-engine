package org.hopper.presentation.component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HSize;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.listeners.IDoLayoutListener;
import org.hopper.presentation.component.listeners.IProcessSourceDataListener;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.datacontext.RenderPageDataContext;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

/**
 * Main component class encapsulating component plugins through IHComponent
 *
 * @author matt
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class HComponent extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private HLayout layout;
  @HopMetadataProperty private IHComponent component;
  @HopMetadataProperty private String rotation;
  @HopMetadataProperty private String transparency;
  @HopMetadataProperty private HSize clipSize;

  @JsonIgnore private List<IProcessSourceDataListener> processSourceDataListeners;
  @JsonIgnore private List<IDoLayoutListener> doLayoutListeners;

  public HComponent() {
    this.processSourceDataListeners = new ArrayList<>();
    this.doLayoutListeners = new ArrayList<>();
  }

  public HComponent(String name, IHComponent component) {
    this();
    this.name = name;
    this.component = component;
  }

  public HComponent(HComponent c) {
    this();
    this.name = c.name;
    if (c.component != null) {
      this.component = c.component.clone();
      this.component.setThemeName(c.component.getThemeName());
    }
    this.layout = c.layout == null ? null : new HLayout(c.layout);
    this.clipSize = c.clipSize == null ? null : new HSize(c.clipSize);
    this.processSourceDataListeners.addAll(c.processSourceDataListeners);
    this.doLayoutListeners.addAll(c.doLayoutListeners);
  }

  @Override
  public String toString() {
    return "HComponent("
        + name
        + ":"
        + (component == null ? "-" : component.getPluginId())
        + ")";
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HComponent)) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    return ((HComponent) obj).name.equals(name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * Process data in the component. Then perform the layout of the component, modify the layout
   * results.
   *
   * @param log The logging channel to log to
   * @param hopperPresentation the presentation
   * @param page the page
   * @param dataContext The data context to use
   */
  public void processAndLayout(
      ILogChannel log,
      HPresentation hopperPresentation,
      HPage page,
      RenderPageDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults footerResults)
      throws HException {
    component.setLogChannel(log);

    // Header/footer path: apply presentation default when theme is unset/blank
    if (component != null
        && (component.getThemeName() == null || component.getThemeName().isBlank())
        && hopperPresentation != null
        && hopperPresentation.getDefaultThemeName() != null
        && !hopperPresentation.getDefaultThemeName().isBlank()) {
      component.setThemeName(hopperPresentation.getDefaultThemeName());
    }

    // Call the process source data listeners...
    //
    for (IProcessSourceDataListener listener : processSourceDataListeners) {
      listener.beforeProcessSourceDataCalled(
          hopperPresentation, page, this, dataContext, renderContext, footerResults);
    }

    component.processSourceData(
        hopperPresentation, page, this, dataContext, renderContext, footerResults);

    // Call the do layout listeners
    //
    component.doLayout(hopperPresentation, page, this, dataContext, renderContext, footerResults);
  }

  /**
   * Build a complete set of all the components this component depends upon for doing layout
   *
   * @param components
   * @return
   */
  public Set<HComponent> getDependentComponents(Map<String, HComponent> components)
      throws HException {
    Set<HComponent> set = new HashSet<>();

    for (String referencedComponentName : layout.getReferencedLayoutComponentNames()) {
      HComponent referencedComponent = components.get(referencedComponentName);
      if (referencedComponent == null) {
        throw new HException(
            "Component "
                + getName()
                + " references "
                + referencedComponentName
                + " which isn't known");
      }
      // Now see if this component is in the list yet...
      //
      if (!set.contains(referencedComponent)) {
        // Do a recursive search and all the referenced components as well...
        //
        set.add(referencedComponent);
        set.addAll(referencedComponent.getDependentComponents(components));
      }
    }

    return set;
  }

  /**
   * Render this component alone on a fresh in-memory presentation: single page (no header/footer),
   * given size, full-page layout. Connectors and themes resolve from {@code metadataProvider}.
   * Used for property-editor previews.
   *
   * @param width page width in pixels
   * @param height page height in pixels
   * @param metadataProvider metadata catalog for connectors and themes
   * @return SVG XML of the rendered page
   */
  public String getSvgXml(int width, int height, IHopMetadataProvider metadataProvider)
      throws HException {
    return getSvgXml(width, height, metadataProvider, null);
  }

  /**
   * Same as {@link #getSvgXml(int, int, IHopMetadataProvider)} but when {@code
   * colorSourcePresentation} is provided, a full layout+render of that presentation is run first
   * so chart series colors ({@code getStableColor}) match the order used on the real page.
   */
  public String getSvgXml(
      int width,
      int height,
      IHopMetadataProvider metadataProvider,
      HPresentation colorSourcePresentation)
      throws HException {

    LoggingObject loggingObject = new LoggingObject("componentPreview");

    // Pre-warm stable series-color maps from the full presentation (same theme discovery order)
    org.hopper.render.context.PresentationRenderContext warmContext = null;
    if (colorSourcePresentation != null) {
      try {
        warmContext =
            new org.hopper.render.context.PresentationRenderContext(
                colorSourcePresentation, metadataProvider);
        HLayoutResults seedResults =
            colorSourcePresentation.doLayout(
                loggingObject, warmContext, metadataProvider, Collections.emptyList());
        colorSourcePresentation.render(seedResults, metadataProvider, warmContext);
      } catch (Exception e) {
        // Preview still works without perfect color matching
        warmContext = null;
      }
    }

    // --- build a throwaway presentation (never saved) ---
    HPresentation presentation = new HPresentation();
    presentation.setName("preview:" + (name != null ? name : "component"));
    presentation.setHeader(null);
    presentation.setFooter(null);
    presentation.setPages(new ArrayList<>());
    presentation.setInteractions(new ArrayList<>());

    String preferredDefaultName =
        colorSourcePresentation != null ? colorSourcePresentation.getDefaultThemeName() : null;
    if (preferredDefaultName == null || preferredDefaultName.isBlank()) {
      preferredDefaultName = org.hopper.core.Constants.DEFAULT_THEME_NAME;
    }
    presentation.setDefaultThemeName(preferredDefaultName);

    // Single page, no margins — page size is the preview canvas
    int pageW = Math.max(1, width);
    int pageH = Math.max(1, height);
    HPage page = new HPage(pageW, pageH, 0, 0, 0, 0);
    page.setHeader(false);
    page.setFooter(false);
    presentation.getPages().add(page);

    // Component copy: fill the preview page (ignore original relative layout)
    HComponent previewComponent = new HComponent(this);
    previewComponent.setLayout(HLayout.fullPage());
    if (previewComponent.getComponent() != null) {
      // Keep explicit theme; otherwise use the same default as the source presentation
      String themeName = previewComponent.getComponent().getThemeName();
      if (themeName == null || themeName.isEmpty()) {
        previewComponent.getComponent().setThemeName(presentation.getDefaultThemeName());
      }
    }
    page.getComponents().add(previewComponent);

    // Layout + render; reuse warmed color maps so series colors match the full page
    org.hopper.render.context.PresentationRenderContext renderContext =
        new org.hopper.render.context.PresentationRenderContext(presentation, metadataProvider);
    if (warmContext != null) {
      // Copy stable-color assignment state from full presentation render
      if (warmContext.getThemeValueColorMap() != null) {
        java.util.Map<String, java.util.Map<String, Integer>> copy = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> e :
            warmContext.getThemeValueColorMap().entrySet()) {
          copy.put(e.getKey(), new java.util.HashMap<>(e.getValue()));
        }
        renderContext.setThemeValueColorMap(copy);
      }
      if (warmContext.getThemeColorIndexMap() != null) {
        renderContext.setThemeColorIndexMap(
            new java.util.HashMap<>(warmContext.getThemeColorIndexMap()));
      }
    }

    HLayoutResults results =
        presentation.doLayout(
            loggingObject, renderContext, metadataProvider, Collections.emptyList());
    presentation.render(results, metadataProvider, renderContext);

    if (results.getRenderPages().isEmpty()) {
      throw new HException("Component preview produced no render pages");
    }
    return results.getRenderPages().get(0).getSvgXml();
  }


  /**
   * @param processSourceDataListeners The processSourceDataListeners to set
   */
  public void setProcessSourceDataListeners(
      List<IProcessSourceDataListener> processSourceDataListeners) {
    this.processSourceDataListeners = processSourceDataListeners;
  }
}
