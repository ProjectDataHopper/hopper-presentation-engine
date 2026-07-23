package org.hopper.render;

import org.hopper.core.HColorRGB;
import org.hopper.core.exception.HException;
import org.hopper.presentation.theme.HTheme;

/** This describes the context in which components need to render their content */
public interface IRenderContext {

  /**
   * Look up the theme with the given name
   *
   * @param themeName The name of the theme to look for
   * @return The theme or null if it couldn't be found
   */
  HTheme lookupTheme(String themeName) throws HException;

  /**
   * Look up the color for a particular string in a given theme. The same value always maps to the
   * same color within a render context (reused after first assignment). Distinct values receive
   * sequential theme palette slots so the first N labels get distinct colors.
   *
   * @param themeName
   * @param value series label, pie slice / category label, etc.
   * @return The color for the theme and value
   */
  HColorRGB getStableColor(String themeName, String value) throws HException;
}
