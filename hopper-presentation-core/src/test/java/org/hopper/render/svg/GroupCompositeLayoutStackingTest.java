package org.hopper.render.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.hopper.core.HGeometry;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.render.IRenderContext;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.util.GroupCompositePresentationUtil;

/**
 * Group → Composite with multiple countries must stack each row below the previous composite,
 * including {@code verticalMargin}, and continue on the current render page after multi-page
 * crosstabs (not jam every Label1 onto page 1).
 */
public class GroupCompositeLayoutStackingTest extends HPresentationTestBase {

  @Test
  public void groupRowsStackWithVerticalMarginAndPageFlow() throws Exception {
    HPresentation presentation =
        new GroupCompositePresentationUtil(metadataProvider, variables)
            .createGroupCompositePresentation(8001);
    IRenderContext renderContext = new PresentationRenderContext(presentation, metadataProvider);
    HLayoutResults results =
        presentation.doLayout(parent, renderContext, metadataProvider, Collections.emptyList());

    assertTrue(results.getRenderPages().size() > 1, "crosstabs should paginate across countries");

    Map<String, Integer> labelPageByName = new HashMap<>();
    for (HRenderPage rp : results.getRenderPages()) {
      if (rp.getLayoutResults() == null) {
        continue;
      }
      for (HComponentLayoutResult lr : rp.getLayoutResults()) {
        if (lr.getComponent() == null || lr.getComponent().getName() == null) {
          continue;
        }
        String name = lr.getComponent().getName();
        if (name.contains("Label1")) {
          labelPageByName.put(name, rp.getPageNumber());
        }
      }
    }

    int rowsFound = 0;
    for (int i = 1; i <= 20; i++) {
      String compositeName = "Group-group#" + i + ":Composite1";
      String labelName = compositeName + "-child(Label1)";
      HGeometry compositeLast = results.findGeometry(compositeName);
      HGeometry labelGeo = results.findGeometry(labelName);
      if (compositeLast == null && labelGeo == null) {
        break;
      }
      rowsFound++;
      assertNotNull(labelGeo, "Label1 geometry for row " + i);
      assertNotNull(compositeLast, "Composite geometry for row " + i);
      assertNotNull(labelPageByName.get(labelName), "Label1 on a render page for row " + i);

      if (i > 1) {
        String prevComposite = "Group-group#" + (i - 1) + ":Composite1";
        HGeometry prevLast = results.findGeometry(prevComposite);
        assertNotNull(prevLast, "Previous composite for row " + i);
        int expectedY = prevLast.getY() + prevLast.getHeight();
        assertEquals(
            expectedY,
            labelGeo.getY(),
            "Row "
                + i
                + " Label1 top must be previous composite bottom (includes verticalMargin)");
      }
    }

    assertTrue(rowsFound >= 2, "expected multiple group rows, got " + rowsFound);

    // Later country labels must not all be forced onto page 1 (regression from getFirstRenderPage)
    boolean anyLabelAfterPage1 = false;
    for (int page : labelPageByName.values()) {
      if (page > 1) {
        anyLabelAfterPage1 = true;
        break;
      }
    }
    assertTrue(
        anyLabelAfterPage1,
        "At least one Label1 after multi-page crosstab must continue past page 1; got "
            + labelPageByName);
  }
}
