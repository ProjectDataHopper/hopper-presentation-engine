package org.hopper.presentation.host;

import java.util.List;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.variable.HParameter;

/**
 * Optional callbacks a GUI host implements for side effects the engine cannot perform (open a Hop
 * file, show an SWT tooltip, launch a URL).
 *
 * <p>Default methods are no-ops so a viewer can navigate {@code OPEN_PRESENTATION} internally
 * without a host.
 */
public interface HPresentationHost {

  default void openPresentation(String name, List<HParameter> parameters) {}

  default void openLink(String url, boolean newTab) {}

  default void showTooltip(String title, String text, DrawnItem item) {}

  default void popupPresentation(String name, List<HParameter> parameters, DrawnItem item) {}
}
