package org.hopper.presentation.simple;

import java.util.Arrays;
import org.hopper.core.Constants;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.presentation.theme.HTheme;

/**
 * Light and dark themes for generated Hop GUI presentations (results Gantt, project dashboards).
 * Distinct from REST {@link HTheme#getDefault()}/{@link HTheme#getDefaultDark()} so Hop GUI
 * surfaces can follow the desktop/web color mode without changing the canvas editor defaults.
 */
public final class HGeneratedThemes {

  private HGeneratedThemes() {}

  /** Light: white page, dark ink, Hop-like blue series. */
  public static HTheme light() {
    HTheme theme = new HTheme();
    theme.setName(Constants.GENERATED_THEME_NAME);
    theme.setDescription("Generated presentation theme for Hop GUI light mode");
    theme
        .getColors()
        .addAll(
            Arrays.asList(
                new HColorRGB("#1f6f8b"),
                new HColorRGB("#2e8a6a"),
                new HColorRGB("#3d5a80"),
                new HColorRGB("#e09f3e"),
                new HColorRGB("#9b2226"),
                new HColorRGB("#6d597a"),
                new HColorRGB("#4c956c"),
                new HColorRGB("#bc4749")));
    theme.setBackgroundColor(new HColorRGB("#ffffff"));
    theme.setDefaultColor(new HColorRGB("#1a1a1a"));
    theme.setDefaultFont(new HFont("Arial", "12", false, false));
    theme.setBorderColor(new HColorRGB("#e6e6e6"));
    theme.setHorizontalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setHorizontalDimensionsColor(new HColorRGB("#1a1a1a"));
    theme.setVerticalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setVerticalDimensionsColor(new HColorRGB("#1a1a1a"));
    theme.setFactsFont(new HFont("Arial", "12", false, false));
    theme.setFactsColor(new HColorRGB("#1a1a1a"));
    theme.setTitleFont(new HFont("Arial", "14", true, false));
    theme.setTitleColor(new HColorRGB("#1a1a1a"));
    theme.setAxisColor(new HColorRGB("#4a4a4a"));
    theme.setGridColor(new HColorRGB("#e0e0e0"));
    theme.setHeaderFont(new HFont("Arial", "12", true, false));
    theme.setHeaderColor(new HColorRGB("#1a1a1a"));
    theme.setHeaderBackGroundColor(new HColorRGB("#f2f2f2"));
    return theme;
  }

  /** Dark: Hop GUI dark-gray surfaces, light ink, brighter series. */
  public static HTheme dark() {
    HTheme theme = new HTheme();
    theme.setName(Constants.GENERATED_DARK_THEME_NAME);
    theme.setDescription("Generated presentation theme for Hop GUI dark mode");
    theme
        .getColors()
        .addAll(
            Arrays.asList(
                new HColorRGB("#5c9fd6"),
                new HColorRGB("#4fc3f7"),
                new HColorRGB("#81c784"),
                new HColorRGB("#ffb74d"),
                new HColorRGB("#e57373"),
                new HColorRGB("#ba68c8"),
                new HColorRGB("#4dd0e1"),
                new HColorRGB("#aed581")));
    theme.setBackgroundColor(new HColorRGB("#3c3f41"));
    theme.setDefaultColor(new HColorRGB("#e6e6e6"));
    theme.setDefaultFont(new HFont("Arial", "12", false, false));
    theme.setBorderColor(new HColorRGB("#4e5153"));
    theme.setHorizontalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setHorizontalDimensionsColor(new HColorRGB("#e6e6e6"));
    theme.setVerticalDimensionsFont(new HFont("Arial", "12", true, false));
    theme.setVerticalDimensionsColor(new HColorRGB("#e6e6e6"));
    theme.setFactsFont(new HFont("Arial", "12", false, false));
    theme.setFactsColor(new HColorRGB("#e6e6e6"));
    theme.setTitleFont(new HFont("Arial", "14", true, false));
    theme.setTitleColor(new HColorRGB("#f0f0f0"));
    theme.setAxisColor(new HColorRGB("#b0b0b0"));
    theme.setGridColor(new HColorRGB("#55585a"));
    theme.setHeaderFont(new HFont("Arial", "12", true, false));
    theme.setHeaderColor(new HColorRGB("#e6e6e6"));
    theme.setHeaderBackGroundColor(new HColorRGB("#4e5153"));
    return theme;
  }
}
