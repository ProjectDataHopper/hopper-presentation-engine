package org.hopper.presentation.datacontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMetaBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HCachingConnector;
import org.hopper.presentation.connector.type.IHConnector;

class HConnectorResultCacheTest {

  @BeforeEach
  void setUp() throws Exception {
    ConnectorTestSupport.initEnvironment();
    HConnectorCacheSettings.setForTests(true, 10_000);
  }

  @AfterEach
  void tearDown() {
    HConnectorCacheSettings.resetToDefaults();
  }

  @Test
  void secondComponentStreamHitsCache() throws Exception {
    AtomicInteger streamCount = new AtomicInteger();
    CountingConnector plugin = new CountingConnector(streamCount, 7);
    HConnector meta = ConnectorTestSupport.wrap("shared-src", plugin);
    PresentationDataContext ctx = ConnectorTestSupport.dataContext(meta);

    HConnector first = ctx.getConnector("shared-src");
    assertInstanceOf(HCachingConnector.class, first.getConnector());
    List<RowMetaAndData> rows1 = first.retrieveRows(ctx);
    assertEquals(7, rows1.size());
    assertEquals(1, streamCount.get(), "first stream executes the connector");

    HConnector second = ctx.getConnector("shared-src");
    List<RowMetaAndData> rows2 = second.retrieveRows(ctx);
    assertEquals(7, rows2.size());
    assertEquals(1, streamCount.get(), "second stream must use the cache");

    HConnectorResultCache cache = ctx.getConnectorResultCache();
    assertEquals(1, cache.getMisses());
    assertEquals(1, cache.getHits());
    assertEquals(1, cache.size());
  }

  @Test
  void oversizedResultIsNotCached() throws Exception {
    HConnectorCacheSettings.setForTests(true, 5);
    AtomicInteger streamCount = new AtomicInteger();
    CountingConnector plugin = new CountingConnector(streamCount, 20);
    PresentationDataContext ctx =
        ConnectorTestSupport.dataContext(ConnectorTestSupport.wrap("big", plugin));

    assertEquals(20, ctx.getConnector("big").retrieveRows(ctx).size());
    assertEquals(1, streamCount.get());
    assertEquals(0, ctx.getConnectorResultCache().size(), "too large to cache");
    assertTrue(ctx.getConnectorResultCache().getSkippedTooLarge() >= 1);

    // Second consumer streams again
    assertEquals(20, ctx.getConnector("big").retrieveRows(ctx).size());
    assertEquals(2, streamCount.get());
  }

  @Test
  void disabledCacheAlwaysStreams() throws Exception {
    HConnectorCacheSettings.setForTests(false, 10_000);
    AtomicInteger streamCount = new AtomicInteger();
    PresentationDataContext ctx =
        ConnectorTestSupport.dataContext(
            ConnectorTestSupport.wrap("x", new CountingConnector(streamCount, 3)));
    // Cache object may exist but isEnabled is false → no wrap
    assertEquals(3, ctx.getConnector("x").retrieveRows(ctx).size());
    assertEquals(3, ctx.getConnector("x").retrieveRows(ctx).size());
    assertEquals(2, streamCount.get());
  }

  /** Counts doStartStreaming invocations (survives clone via shared AtomicInteger). */
  static final class CountingConnector extends HBaseConnector implements IHConnector {
    private final AtomicInteger streamCount;
    private final int rowCount;

    CountingConnector(AtomicInteger streamCount, int rowCount) {
      super("CountingConnector");
      this.streamCount = streamCount;
      this.rowCount = rowCount;
    }

    @Override
    public CountingConnector clone() {
      return new CountingConnector(streamCount, rowCount);
    }

    @Override
    public IRowMeta describeOutput(IDataContext dataContext) {
      return new RowMetaBuilder().addInteger("id").addString("label").build();
    }

    @Override
    protected void doStartStreaming(IDataContext dataContext) throws HException {
      streamCount.incrementAndGet();
      IRowMeta meta = describeOutput(dataContext);
      for (int i = 0; i < rowCount; i++) {
        Object[] row = RowDataUtil.allocateRowData(meta.size());
        row[0] = (long) (i + 1);
        row[1] = "r" + (i + 1);
        passToRowListeners(meta, row);
      }
      outputDone();
    }

    @Override
    public void waitUntilFinished() {
      // sync
    }
  }
}
