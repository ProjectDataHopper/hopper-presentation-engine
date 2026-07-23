package org.hopper.render.context;

import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
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
   * Resolve a theme by name from the metadata catalog. Blank/null uses the presentation default
   * theme name. Falls back to {@link HTheme#getDefault()} when nothing is found.
   *
   * @param themeName The name of the theme to look for, or null/blank for the presentation default
   * @return The theme (never null for normal use; built-in default as last resort)
   */
  @Override
  public HTheme lookupTheme(String themeName) throws HException {
    String name = themeName;
    if (StringUtils.isBlank(name) && presentation != null) {
      name = presentation.getDefaultThemeName();
    }

    if (StringUtils.isNotBlank(name)) {
      // Local SimpleRenderContext list first (preview color warming), then metadata
      HTheme theme = super.lookupTheme(name);
      if (theme != null) {
        return theme;
      }
    }

    return HTheme.getDefault();
  }
}
