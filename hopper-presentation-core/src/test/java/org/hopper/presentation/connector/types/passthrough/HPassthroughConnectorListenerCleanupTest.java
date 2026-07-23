package org.hopper.presentation.connector.types.passthrough;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.list.HListConnector;
import org.hopper.presentation.datacontext.PresentationDataContext;

/**
 * Verifies transform connectors detach from the source after {@code waitUntilFinished}, so a
 * reused source instance does not accumulate listeners across runs.
 */
class HPassthroughConnectorListenerCleanupTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
  }

  @Test
  void secondRunDoesNotStackListenersOnSharedSource() throws Exception {
    HListConnector list = new HListConnector("v", Arrays.asList("a", "b"));

    // First pass
    assertEquals(2, runPassthrough(list).size());
    assertEquals(0, list.getRowListeners().size(), "listener should be detached after finish");

    // Second pass on the same source instance
    assertEquals(2, runPassthrough(list).size());
    assertEquals(0, list.getRowListeners().size(), "still no leftover listeners after second run");
  }

  private List<RowMetaAndData> runPassthrough(HListConnector sharedSource) throws Exception {
    HConnector sourceWrap = new HConnector("source", sharedSource);
    HPassthroughConnector transform = new HPassthroughConnector("source");
    HConnector transformWrap = new HConnector("pass", transform);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(sourceWrap, transformWrap);
    // retrieveRows uses dataContext.getConnector which copies connectors — that hides the bug.
    // Stream against the shared instance by calling the transform with a context whose
    // getConnector("source") returns the same wrapper each time (no copy).
    PresentationDataContext noCopy =
        new PresentationDataContext(ctx.getPresentation(), ctx.getMetadataProvider()) {
          @Override
          public HConnector getConnector(String name) {
            if ("source".equals(name)) {
              return sourceWrap;
            }
            if ("pass".equals(name)) {
              return transformWrap;
            }
            return null;
          }
        };
    return transformWrap.retrieveRows(noCopy);
  }
}
