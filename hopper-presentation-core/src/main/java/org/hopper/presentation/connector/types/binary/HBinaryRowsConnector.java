package org.hopper.presentation.connector.types.binary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.core.row.HHopRowsFile;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Source connector that streams rows from a Hop binary row file written with {@link
 * org.apache.hop.core.row.IRowMeta#writeMeta}/{@code writeData}.
 */
@JsonDeserialize(as = HBinaryRowsConnector.class)
@HConnectorPlugin(
    id = "BinaryRowsConnector",
    name = "Binary Hop rows",
    description = "Reads rows from a Hop binary row file (writeMeta/writeData format)",
    image = "ui/images/connectors/binary-rows.svg")
@Getter
@Setter
public class HBinaryRowsConnector extends HBaseConnector implements IHConnector {

  @HWidgetElement(
      order = "10000-filename",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.FILENAME,
      label = "Filename",
      toolTip =
          "Apache Commons VFS path to a .hoprows file (e.g."
              + " ${HOPPER_DATA_PATH}/timings/My Presentation/latest.hoprows)")
  @HopMetadataProperty
  private String filename;

  @HWidgetElement(
      order = "10100-limit",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Limit",
      toolTip = "Max data rows to read (0 = unlimited)")
  @HopMetadataProperty
  private int limit = 0;

  public HBinaryRowsConnector() {
    super("BinaryRowsConnector");
  }

  public HBinaryRowsConnector(HBinaryRowsConnector c) {
    super(c);
    this.filename = c.filename;
    this.limit = c.limit;
  }

  @Override
  public HBinaryRowsConnector clone() {
    return new HBinaryRowsConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    String path = resolvePath(dataContext);
    if (StringUtils.isBlank(path) || !HHopRowsFile.exists(path)) {
      return new RowMeta();
    }
    return HHopRowsFile.readMeta(path);
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    String path = resolvePath(dataContext);
    if (StringUtils.isBlank(path)) {
      throw new HException("Binary Hop rows filename is empty");
    }
    if (!HHopRowsFile.exists(path)) {
      throw new HException("Binary Hop rows file not found: " + path);
    }
    HHopRowsFile.Snapshot snap = HHopRowsFile.read(path, Math.max(0, limit));
    IRowMeta meta = snap.getRowMeta() != null ? snap.getRowMeta() : new RowMeta();
    for (Object[] row : snap.getRows()) {
      passToRowListeners(meta, row);
    }
    outputDone();
  }

  @Override
  public void waitUntilFinished() throws HException {
    // Synchronous
  }

  private String resolvePath(IDataContext dataContext) {
    IVariables variables = dataContext != null ? dataContext.getVariables() : null;
    String raw = Const.NVL(filename, "").trim();
    if (variables != null) {
      raw = Const.NVL(variables.resolve(raw), "").trim();
    }
    return raw;
  }
}
