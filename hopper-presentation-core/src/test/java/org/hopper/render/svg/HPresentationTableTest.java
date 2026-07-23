package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.TablePresentationUtil;

public class HPresentationTableTest extends HPresentationTestBase {

  @Test
  public void testTableRender() throws Exception {

    HPresentation presentation =
        new TablePresentationUtil(metadataProvider, variables).createTablePresentation(2000);
    testRendering(presentation, "table_test");
  }
}
