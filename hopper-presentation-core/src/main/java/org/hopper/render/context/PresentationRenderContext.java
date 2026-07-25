package org.hopper.render.context;

import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HColorMode;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.theme.HTheme;
import org.hopper.render.IRenderContext;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresentationRenderContext extends SimpleRenderContext implements IRenderContext {

  private HPresentation presentation;

  public PresentationRenderContext(IHopMetadataProvider metadataProvider) {
    super(metadataProvider);
  }

  public PresentationRenderContext(
      HPresentation presentation, IHopMetadataProvider metadataProvider) {
    this(metadataProvider);
    this.presentation = presentation;
  }

  /**
   * Resolve a theme by name from the metadata catalog.
   *
   * <p>Blank/null {@code themeName} uses the presentation theme for the active {@link
   * #getColorMode()}: light → {@code defaultThemeName}, dark → {@code darkThemeName} or a derived
   * dark variant of the light theme.
   *
   * <p>When {@code themeName} equals the presentation's {@code defaultThemeName}, that is treated
   * as "use presentation default" (not a hard lock to the light catalog theme). Layout code used to
   * stamp blank component themes with {@code defaultThemeName}, which forced light ink in dark
   * mode; remapping keeps those components mode-aware.
   *
   * <p>Any other explicit theme name is mode-invariant (catalog load as-is).
   *
   * @param themeName The name of the theme to look for, or null/blank for the presentation default
   * @return The theme (never null for normal use; built-in default as last resort)
   */
  @Override
  public HTheme lookupTheme(String themeName) throws HException {
    if (usesPresentationModeDefault(themeName)) {
      // Component left themeName blank/null (or stamped the presentation light default): use the
      // presentation light/dark pair for the active color mode.
      HTheme resolved =
          presentation != null
              ? presentation.resolveDefaultTheme(getMetadataProvider(), getColorMode())
              : null;
      return ensureRenderable(resolved);
    }

    // Explicit non-default theme: mode-invariant catalog lookup
    HTheme theme = null;
    try {
      theme = super.lookupTheme(themeName);
    } catch (HException e) {
      // Catalog missing / unreadable — fall through to built-in
      theme = null;
    }
    return ensureRenderable(theme);
  }

  /**
   * True when the component should follow the presentation light/dark theme pair for the active
   * color mode.
   *
   * <p>Blank/null (and the literal string {@code "null"} from bad JSON/form saves) mean "use
   * presentation default". The presentation's {@code defaultThemeName} is also treated as mode
   * default so stamped light names still track dark mode.
   */
  boolean usesPresentationModeDefault(String themeName) {
    if (StringUtils.isBlank(themeName) || "null".equalsIgnoreCase(themeName.trim())) {
      return true;
    }
    if (presentation == null) {
      return false;
    }
    String light = presentation.getDefaultThemeName();
    return StringUtils.isNotBlank(light) && light.equalsIgnoreCase(themeName.trim());
  }

  /** Never hand components an empty/half-deserialized theme object. */
  private HTheme ensureRenderable(HTheme theme) {
    if (theme != null && theme.isRenderable()) {
      return theme;
    }
    return getColorMode() == HColorMode.DARK ? HTheme.getDefaultDark() : HTheme.getDefault();
  }
}
