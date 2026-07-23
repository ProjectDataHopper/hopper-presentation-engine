package org.hopper.presentation.connector.preview;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Structured result of {@link ConnectorPreviewSupport#preview}. Safe to serialize for the browser
 * (row meta as plain fields + display-string cells).
 */
@Getter
@Setter
@NoArgsConstructor
public class ConnectorPreviewResult {

  private boolean ok = true;
  private int maxRows;
  private SampleSide input;
  private SampleSide output;
  private PreviewError error;

  @Getter
  @Setter
  @NoArgsConstructor
  public static class SampleSide {
    private String connectorName;
    /** Field layout: name, type, length, precision. */
    private List<ValueMetaInfo> rowMeta = new ArrayList<>();
    /** Display-string cells; column order matches {@link #rowMeta}. */
    private List<List<String>> rows = new ArrayList<>();
    private boolean truncated;
    private int rowCountReturned;

    /** Set when this side failed; overall result may still include the other side. */
    private String errorSummary;

    private String errorDetail;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class ValueMetaInfo {
    private String name;
    private String type;
    private int length;
    private int precision;

    public ValueMetaInfo(String name, String type, int length, int precision) {
      this.name = name;
      this.type = type;
      this.length = length;
      this.precision = precision;
    }
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class PreviewError {
    private String summary;
    private String detail;

    public PreviewError(String summary, String detail) {
      this.summary = summary;
      this.detail = detail != null ? detail : summary;
    }
  }
}
