package org.hopper.render.svg;

import org.junit.jupiter.api.Test;
import org.hopper.presentation.HPresentation;
import org.hopper.util.TablePresentationUtil;

public class HPresentationTableChainTest extends HPresentationTestBase {

  @Test
  public void testTableChainRender() throws Exception {

    HPresentation presentation =
        new TablePresentationUtil(metadataProvider, variables).createTableChainPresentation(2100);
    testRendering(presentation, "table_chain_test");
  }
}
