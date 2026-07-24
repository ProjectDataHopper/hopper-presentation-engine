package org.hopper.render.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
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
   * Look for the theme scheme with the given name
   *
   * @param themeName the scheme name to look for
   * @return The theme scheme or null if nothing could be found
   */
  @Override
  public HTheme lookupTheme(String themeName) throws HException {
    for (HTheme theme : themes) {
      if (theme.getName().equalsIgnoreCase(themeName)) {
        return theme;
      }
    }
    // Try again in the metadata
    //
    try {
      return metadataProvider.getSerializer(HTheme.class).load(themeName);
    } catch (Exception e) {
      throw new HException("Error loading theme '" + themeName + "' from the metadata", e);
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
