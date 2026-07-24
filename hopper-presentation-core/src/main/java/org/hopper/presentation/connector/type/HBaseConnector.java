package org.hopper.presentation.connector.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.audit.lineage.HConnectorRun;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.datacontext.IDataContext;

@Getter
@Setter
public abstract class HBaseConnector implements IHConnector {

  @HopMetadataProperty @JsonProperty protected String pluginId;

  @HWidgetElement(
      order = "01000-sourceConnectorName",
      parentId = HGuiFormConstants.PARENT_BASE,
      type = HWidgetType.COMBO,
      comboSource = org.hopper.core.gui.plugin.HComboSource.CONNECTORS,
      label = "Source connector",
      toolTip = "Upstream connector this transform reads from (if applicable)")
  @HopMetadataProperty
  @JsonProperty
  protected String sourceConnectorName;

  @JsonIgnore protected List<IHRowListener> rowListeners;

  /**
   * Source connector this transform attached a listener to (runtime only; not metadata). Cleared
   * after {@link #detachFromSource()}.
   */
  @JsonIgnore private transient IHConnector attachedSource;

  /** Listener registered on {@link #attachedSource} (runtime only). */
  @JsonIgnore private transient IHRowListener attachedListener;

  /** Active execution run for lineage (runtime only). */
  @JsonIgnore private transient HConnectorRun activeRun;

  public HBaseConnector(String pluginId) {
    this.pluginId = pluginId;
    rowListeners = new ArrayList<>();
  }

  public HBaseConnector(HBaseConnector c) {
    this.pluginId = c.pluginId;
    this.sourceConnectorName = c.sourceConnectorName;
    // We don't copy over the listeners!
    //
    this.rowListeners = new ArrayList<>();
  }

  /**
   * Register a row listener on a source connector and remember it so {@link #detachFromSource()}
   * can clean up after streaming.
   */
  protected void attachToSource(IHConnector source, IHRowListener listener) throws HException {
    if (source == null) {
      throw new HException("Cannot attach to a null source connector");
    }
    if (listener == null) {
      throw new HException("Cannot attach a null row listener");
    }
    // Replace any previous attachment to avoid stacking listeners on reuse.
    detachFromSource();
    source.addRowListener(listener);
    this.attachedSource = source;
    this.attachedListener = listener;
  }

  /**
   * Remove the listener previously registered with {@link #attachToSource(IHConnector,
   * IHRowListener)}. Safe to call multiple times.
   */
  protected void detachFromSource() {
    if (attachedSource != null && attachedListener != null) {
      attachedSource.removeDataListener(attachedListener);
    }
    attachedSource = null;
    attachedListener = null;
  }

  public abstract HBaseConnector clone();

  /**
   * @return Null if the dialog class is determined automatically. Otherwise returns the dialog
   *     class name.
   */
  @JsonIgnore
  public String getDialogClassname() {
    return null;
  }

  /**
   * Signal to all row listeners that no more rows will be forthcoming by writing a null row
   *
   * @throws HException
   */
  public void outputDone() throws HException {
    for (IHRowListener rowListener : rowListeners) {
      rowListener.rowReceived(null, null);
    }
  }

  public void passToRowListeners(IRowMeta rowMeta, Object[] rowData) throws HException {
    if (rowData != null && activeRun != null) {
      activeRun.incrementRowCount();
    }
    for (IHRowListener rowListener : rowListeners) {
      rowListener.rowReceived(rowMeta, rowData);
    }
  }

  /**
   * Starts streaming with optional execution-lineage capture. Subclasses implement {@link
   * #doStartStreaming(IDataContext)}.
   */
  @Override
  public final void startStreaming(IDataContext dataContext) throws HException {
    HExecutionTrace trace = dataContext != null ? dataContext.getExecutionTrace() : null;
    HConnectorRun run = null;
    if (trace != null && !trace.isNoop()) {
      run = trace.beginConnectorRun(pluginId, sourceConnectorName);
      enrichConnectorRun(run, dataContext);
      this.activeRun = run;
    }
    try {
      doStartStreaming(dataContext);
      if (run != null) {
        run.completeSuccess();
      }
    } catch (HException e) {
      if (run != null) {
        run.completeFailure(e);
      }
      throw e;
    } catch (RuntimeException e) {
      if (run != null) {
        run.completeFailure(e);
      }
      throw e;
    } finally {
      this.activeRun = null;
    }
  }

  /** Connector-specific streaming implementation (wrapped by lineage-aware {@link #startStreaming}). */
  protected abstract void doStartStreaming(IDataContext dataContext) throws HException;

  /**
   * Optional hook for source connectors to attach SQL, connection name, URL, etc. to the lineage
   * run before streaming begins.
   */
  protected void enrichConnectorRun(HConnectorRun run, IDataContext dataContext) {
    // default: nothing extra
  }

  public abstract void waitUntilFinished() throws HException;

  @Override
  public void addRowListener(IHRowListener rowListener) throws HException {
    rowListeners.add(rowListener);
  }

  @Override
  public void removeDataListener(IHRowListener rowListener) {
    rowListeners.remove(rowListener);
  }
}
