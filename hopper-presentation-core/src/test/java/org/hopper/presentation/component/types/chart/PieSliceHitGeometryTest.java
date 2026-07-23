package org.hopper.presentation.component.types.chart;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.hopper.core.HGeometry;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.render.svg.HPresentationTestBase;
import org.hopper.util.PieChartPresentationUtil;

/**
 * Slice hit geometries must cover the drawn pie, not tiny mid-arc boxes. Edit-mode hover prefers
 * the union of ComponentItems when it is smaller than the layout envelope.
 */
class PieSliceHitGeometryTest extends HPresentationTestBase {

  @Test
  void sliceHitUnionCoversMostOfPieDiameter() throws Exception {
    HPresentation presentation =
        new PieChartPresentationUtil(metadataProvider, variables).createPieChartPresentation(9901);

    PresentationRenderContext renderContext =
        new PresentationRenderContext(presentation, metadataProvider);
    HLayoutResults results =
        presentation.doLayout(parent, renderContext, metadataProvider, Collections.emptyList());
    presentation.render(results, metadataProvider);

    HRenderPage page = results.getRenderPages().get(0);
    List<DrawnItem> items = page.getDrawnItems();
    assertFalse(items.isEmpty());

    HGeometry componentEnvelope = null;
    HGeometry sliceUnion = null;
    for (DrawnItem item : items) {
      if (!PieChartPresentationUtil.PIE_CHART_NAME.equals(item.getComponentName())) {
        continue;
      }
      if (item.getType() == DrawnItem.DrawnItemType.Component) {
        componentEnvelope = item.getGeometry();
      } else if (item.getType() == DrawnItem.DrawnItemType.ComponentItem
          && DrawnItem.Category.ChartLabel.name().equals(item.getCategory())
          && item.getGeometry() != null) {
        sliceUnion = union(sliceUnion, item.getGeometry());
      }
    }

    assertTrue(componentEnvelope != null, "component envelope DrawnItem required");
    assertTrue(sliceUnion != null, "expected ChartLabel slice hit items");

    // Full pie ink should be a large fraction of the component (not ~outerRadius*0.25 boxes).
    // With legend on the right, width is less than full component; height should still cover most
    // of the pie content band.
    double widthRatio = (double) sliceUnion.getWidth() / Math.max(1, componentEnvelope.getWidth());
    double heightRatio =
        (double) sliceUnion.getHeight() / Math.max(1, componentEnvelope.getHeight());

    assertTrue(
        widthRatio > 0.35,
        "slice hit union width should be substantial vs component (was " + widthRatio + ")");
    assertTrue(
        heightRatio > 0.45,
        "slice hit union height should be substantial vs component (was " + heightRatio + ")");

    // Absolute size: at least ~diameter worth of coverage (radius was wrongly used as half-size)
    assertTrue(
        sliceUnion.getWidth() > 120 && sliceUnion.getHeight() > 120,
        "slice hit union too small: " + sliceUnion);
  }

  private static HGeometry union(HGeometry a, HGeometry b) {
    if (a == null) {
      return new HGeometry(b);
    }
    int x1 = Math.min(a.getX(), b.getX());
    int y1 = Math.min(a.getY(), b.getY());
    int x2 = Math.max(a.getX() + a.getWidth(), b.getX() + b.getWidth());
    int y2 = Math.max(a.getY() + a.getHeight(), b.getY() + b.getHeight());
    return new HGeometry(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
  }
}
