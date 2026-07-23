package org.hopper.presentation.connector.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.connector.preview.ConnectorPreviewResult.SampleSide;
import org.hopper.presentation.connector.preview.ConnectorPreviewResult.ValueMetaInfo;

/**
 * Collects a limited sample of input (upstream) and output rows for the connector data studio.
 *
 * <p>Does not throw for expected runtime failures; returns a structured {@link
 * ConnectorPreviewResult}. Buffering transforms may still process their full input; only the
 * payload kept for the client is capped at {@code maxRows}.
 */
public final class ConnectorPreviewSupport {

  public static final int DEFAULT_MAX_ROWS = 20;
  public static final int HARD_MAX_ROWS = 100;

  private ConnectorPreviewSupport() {}

  /**
   * Clamp a requested sample size into {@code 1..HARD_MAX_ROWS}, defaulting to {@link
   * #DEFAULT_MAX_ROWS} when null or non-positive.
   */
  public static int clampMaxRows(Integer requested) {
    if (requested == null || requested <= 0) {
      return DEFAULT_MAX_ROWS;
    }
    return Math.min(requested, HARD_MAX_ROWS);
  }

  /**
   * Preview input (if {@code sourceConnectorName} is set) and output samples for the given
   * connector instance under {@code dataContext}.
   */
  public static ConnectorPreviewResult preview(
      IDataContext dataContext, HConnector connector, int maxRows) {
    int limit = clampMaxRows(maxRows);
    ConnectorPreviewResult result = new ConnectorPreviewResult();
    result.setMaxRows(limit);
    result.setOk(true);

    if (connector == null || connector.getConnector() == null) {
      result.setOk(false);
      result.setError(
          new ConnectorPreviewResult.PreviewError(
              "Connector definition is empty", "connector or connector.plugin payload is null"));
      return result;
    }

    IHConnector plugin = connector.getConnector();
    String sourceName = plugin.getSourceConnectorName();

    // --- Input sample (upstream) ---
    if (StringUtils.isNotBlank(sourceName)) {
      try {
        HConnector source = dataContext.getConnector(sourceName);
        if (source == null || source.getConnector() == null) {
          result.setOk(false);
          result.setError(
              new ConnectorPreviewResult.PreviewError(
                  "Unable to find source connector '" + sourceName + "'",
                  "Source connector '"
                      + sourceName
                      + "' is not available in the presentation or shared metadata"));
        } else {
          SampleSide input = sampleSide(sourceName, source, dataContext, limit);
          result.setInput(input);
          if (input.getErrorSummary() != null) {
            result.setOk(false);
            result.setError(
                new ConnectorPreviewResult.PreviewError(
                    input.getErrorSummary(), input.getErrorDetail()));
          }
        }
      } catch (Exception e) {
        result.setOk(false);
        result.setError(
            new ConnectorPreviewResult.PreviewError(
                HPresentation.summarizeException(e),
                HPresentation.formatExceptionDetail(e)));
      }
    }

    // --- Output sample (connector under edit) ---
    try {
      SampleSide output = sampleSide(connector.getName(), connector, dataContext, limit);
      result.setOutput(output);
      if (output.getErrorSummary() != null) {
        result.setOk(false);
        // Prefer output error as the primary banner when both fail; keep input if present
        result.setError(
            new ConnectorPreviewResult.PreviewError(
                output.getErrorSummary(), output.getErrorDetail()));
      }
    } catch (Exception e) {
      result.setOk(false);
      result.setError(
          new ConnectorPreviewResult.PreviewError(
              HPresentation.summarizeException(e),
              HPresentation.formatExceptionDetail(e)));
    }

    return result;
  }

  /**
   * Describe + limited sample for one connector. On failure, returns a side with any partial {@code
   * rowMeta} and error fields set (does not throw).
   */
  static SampleSide sampleSide(
      String connectorName, HConnector connector, IDataContext dataContext, int maxRows) {
    SampleSide side = new SampleSide();
    side.setConnectorName(connectorName);

    // Prefer describe first so layout details work when sampling fails
    try {
      IRowMeta described = connector.describeOutput(dataContext);
      side.setRowMeta(toRowMetaList(described));
    } catch (Exception e) {
      side.setErrorSummary(HPresentation.summarizeException(e));
      side.setErrorDetail(HPresentation.formatExceptionDetail(e));
      // Still attempt sample — some connectors only fail describe in odd cases
    }

    try {
      SampleRows sample = collectSampleRows(connector, dataContext, maxRows);
      if (sample.rowMeta != null) {
        side.setRowMeta(toRowMetaList(sample.rowMeta));
      }
      side.setRows(sample.rows);
      side.setTruncated(sample.truncated);
      side.setRowCountReturned(sample.rows.size());
      // Successful sample supersedes a describe-only failure (stale error banner)
      if (side.getErrorSummary() != null
          && sample.rows != null
          && !sample.rows.isEmpty()) {
        side.setErrorSummary(null);
        side.setErrorDetail(null);
      }
    } catch (Exception e) {
      if (side.getErrorSummary() == null) {
        side.setErrorSummary(HPresentation.summarizeException(e));
        side.setErrorDetail(HPresentation.formatExceptionDetail(e));
      } else {
        // Keep describe error as summary; append sample failure to detail
        side.setErrorDetail(
            side.getErrorDetail()
                + "\n\nSample failed: "
                + HPresentation.formatExceptionDetail(e));
      }
    }

    return side;
  }

  private static SampleRows collectSampleRows(
      HConnector hopperConnector, IDataContext dataContext, int maxRows) throws HException {
    IHConnector connector = hopperConnector.getConnector();
    if (connector == null) {
      throw new HException("Connector plugin instance is null");
    }

    List<List<String>> rows = new ArrayList<>();
    AtomicReference<IRowMeta> metaRef = new AtomicReference<>();
    AtomicBoolean truncated = new AtomicBoolean(false);
    AtomicInteger kept = new AtomicInteger(0);
    ArrayBlockingQueue<Object> finishedQueue = new ArrayBlockingQueue<>(10);

    IHRowListener listener =
        (rowMeta, rowData) -> {
          if (rowData == null) {
            finishedQueue.offer(new Object());
            return;
          }
          if (rowMeta != null) {
            metaRef.compareAndSet(null, rowMeta);
          }
          if (kept.get() < maxRows) {
            rows.add(toDisplayRow(rowMeta, rowData));
            kept.incrementAndGet();
          } else {
            truncated.set(true);
          }
        };

    try {
      connector.addRowListener(listener);
      connector.startStreaming(dataContext);

      // Wait for end-of-stream (same pattern as HConnector.retrieveRows)
      while (finishedQueue.poll(1L, TimeUnit.DAYS) == null) {
        // wait
      }
      connector.waitUntilFinished();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HException("Interrupted while sampling connector rows", e);
    } catch (Exception e) {
      if (e instanceof HException hopperException) {
        throw hopperException;
      }
      throw new HException("Error sampling rows from connector", e);
    } finally {
      try {
        connector.removeDataListener(listener);
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }

    SampleRows sample = new SampleRows();
    sample.rowMeta = metaRef.get();
    sample.rows = rows;
    // True only when at least one extra row was seen beyond maxRows
    sample.truncated = truncated.get();
    return sample;
  }

  private static List<String> toDisplayRow(IRowMeta rowMeta, Object[] rowData) {
    List<String> cells = new ArrayList<>();
    if (rowMeta == null || rowData == null) {
      return cells;
    }
    for (int i = 0; i < rowMeta.size(); i++) {
      cells.add(cellToString(rowMeta.getValueMeta(i), rowData, i));
    }
    return cells;
  }

  private static String cellToString(IValueMeta valueMeta, Object[] rowData, int index) {
    if (valueMeta == null) {
      return "";
    }
    try {
      Object value = index < rowData.length ? rowData[index] : null;
      if (value == null) {
        return "";
      }
      String s = valueMeta.getString(value);
      return s != null ? s : "";
    } catch (Exception e) {
      try {
        Object value = index < rowData.length ? rowData[index] : null;
        return value != null ? String.valueOf(value) : "";
      } catch (Exception ignored) {
        return "";
      }
    }
  }

  static List<ValueMetaInfo> toRowMetaList(IRowMeta rowMeta) {
    List<ValueMetaInfo> list = new ArrayList<>();
    if (rowMeta == null) {
      return list;
    }
    for (IValueMeta v : rowMeta.getValueMetaList()) {
      if (v == null) {
        continue;
      }
      list.add(new ValueMetaInfo(v.getName(), v.getTypeDesc(), v.getLength(), v.getPrecision()));
    }
    return list;
  }

  private static final class SampleRows {
    IRowMeta rowMeta;
    List<List<String>> rows = new ArrayList<>();
    boolean truncated;
  }
}
