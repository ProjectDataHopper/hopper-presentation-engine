package org.hopper.render.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HColorMode;
import org.hopper.core.HColorRGB;
import org.hopper.core.HSize;
import org.hopper.core.exception.HException;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;

@Getter
@Setter
public class SimpleRenderContext implements IRenderContext {
  private final IHopMetadataProvider metadataProvider;

  private HSize canvasSize;
  private List<HTheme> themes;

  /** Active color mode for theme resolution (default light). */
  private HColorMode colorMode = HColorMode.LIGHT;

  /**
   * When true (default), non-flowing components that do not fit the usable page height are pushed
   * onto a new render page. When false (editor), they stay on the first sheet with
   * {@code overflowsPage} so designers can still nudge them.
   */
  private boolean allowPeerPageBreak = true;

  /**
   * Maximum body render pages for this layout (tables/crosstabs/groups stop overflowing past this).
   * Default follows admin {@code server.layout.max-render-pages}.
   */
  private int maxRenderPages =
      org.hopper.presentation.layout.HLayoutPageLimitSettings.DEFAULT_MAX_RENDER_PAGES;

  /**
   * When true, layout is a single tall surface for browser vertical scroll (see {@link
   * org.hopper.presentation.layout.HLayoutMode#CONTINUOUS}). Also set automatically when the
   * presentation {@code layoutMode} is continuous.
   */
  private boolean continuousScroll;

  /**
   * Client viewport width in CSS px for continuous layout. {@code 0} means use presentation {@code
   * designWidth} or {@link org.hopper.core.Constants#DEFAULT_CONTINUOUS_DESIGN_WIDTH}.
   */
  private int viewportWidth;

  /**
   * Max usable content height (CSS px) for continuous layout before truncation. Default {@link
   * org.hopper.core.Constants#DEFAULT_MAX_CONTINUOUS_CONTENT_HEIGHT}.
   */
  private int maxContinuousContentHeight =
      org.hopper.core.Constants.DEFAULT_MAX_CONTINUOUS_CONTENT_HEIGHT;

  /** themeName → (series/slice label → palette index). */
  private Map<String, Map<String, Integer>> themeValueColorMap;

  /** themeName → last sequential palette index assigned for a new label. */
  private Map<String, Integer> themeColorIndexMap;

  public SimpleRenderContext(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
    themes = new ArrayList<>();
    themeValueColorMap = new HashMap<>();
    themeColorIndexMap = new HashMap<>();
    colorMode = HColorMode.LIGHT;
  }

  public SimpleRenderContext(
      int width, int height, List<HTheme> themes, IHopMetadataProvider metadataProvider) {
    this(new HSize(width, height), themes, metadataProvider);
  }

  public SimpleRenderContext(
      HSize canvasSize, List<HTheme> themes, IHopMetadataProvider metadataProvider) {
    this(metadataProvider);
    this.canvasSize = canvasSize;
    this.themes = themes;
  }

  /**
   * Look for the theme scheme with the given name.
   *
   * <p>Blank/null names are not catalog keys (Hop {@code load(null)} throws). Callers that want
   * the presentation default must use {@link PresentationRenderContext}, which remaps blank
   * names before calling here.
   *
   * @param themeName the scheme name to look for
   * @return The theme scheme or null if nothing could be found
   */
  @Override
  public HTheme lookupTheme(String themeName) throws HException {
    if (StringUtils.isBlank(themeName)) {
      return null;
    }
    String key = themeName.trim();
    if (themes != null) {
      for (HTheme theme : themes) {
        if (theme != null
            && theme.getName() != null
            && theme.getName().equalsIgnoreCase(key)) {
          return theme;
        }
      }
    }
    // Try again in the metadata
    //
    try {
      if (metadataProvider == null) {
        return null;
      }
      return metadataProvider.getSerializer(HTheme.class).load(key);
    } catch (Exception e) {
      throw new HException("Error loading theme '" + key + "' from the metadata", e);
    }
  }

  /**
   * Resolve a theme palette color for {@code value}.
   *
   * <p>Each distinct label is assigned the next free palette index the first time it is seen in
   * this render context, then that mapping is reused forever. That gives distinct colors for the
   * first N categories (N = palette size) without hash collisions.
   *
   * <p>When charts share the same labels (e.g. pie slices and bars colored by category), the second
   * chart reuses the first chart’s mapping — so matching labels keep matching colors regardless of
   * which component paints first. (A bare bar chart series with an empty label is a different key
   * from category names; use “Color bars by category” for cross-chart matching.)
   */
  @Override
  public HColorRGB getStableColor(String themeName, String value) throws HException {

    HTheme theme = lookupTheme(themeName);
    if (theme == null) {
      return null;
    }
    List<HColorRGB> palette = theme.getColors();
    if (palette == null || palette.isEmpty()) {
      return null;
    }

    String key = value != null ? value : "";
    Map<String, Integer> valueColorMap =
        themeValueColorMap.computeIfAbsent(themeName, n -> new HashMap<>());

    Integer existing = valueColorMap.get(key);
    int colorIndex;
    if (existing != null) {
      colorIndex = existing;
    } else {
      // First time we see this label: next sequential palette slot (wraps if needed)
      Integer last = themeColorIndexMap.get(themeName);
      if (last == null) {
        colorIndex = 0;
      } else {
        colorIndex = last + 1;
        if (colorIndex >= palette.size()) {
          colorIndex = 0;
        }
      }
      themeColorIndexMap.put(themeName, colorIndex);
      valueColorMap.put(key, colorIndex);
    }

    if (colorIndex < 0 || colorIndex >= palette.size()) {
      colorIndex = 0;
    }
    return palette.get(colorIndex);
  }
}
