package org.hopper.presentation.swt;

/**
 * How much viewer chrome to show around the rendered SVG.
 *
 * <ul>
 *   <li>{@link #FULL} — home, zoom (fit page/width/height, in/out, 100%), paging, refresh, export
 *   <li>{@link #ZOOM} — zoom (in/out/100%/width/height/page), live refresh rate, SVG/PDF export
 *       (results panes)
 *   <li>{@link #MINIMAL} — refresh and paging only (dialogs, cards); still auto-fits the canvas
 *   <li>{@link #NONE} — SVG scaled to the parent (side-pane charts)
 * </ul>
 */
public enum HPresentationChrome {
  FULL,
  ZOOM,
  MINIMAL,
  NONE
}
