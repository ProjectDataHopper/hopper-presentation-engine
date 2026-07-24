package org.hopper.presentation.connector.types.sql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.sql.ResultSet;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HComboSource;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.audit.lineage.HConnectorRun;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.security.HAction;
import org.hopper.security.HResourceRef;
import org.hopper.security.HSecurityContext;

@JsonDeserialize(as = HSqlConnector.class)
@HConnectorPlugin(
    id = "SqlConnector",
    name = "Execute a SQL query",
    description = "Reads data from a relational database using a SQL query",
    image = "ui/images/connectors/sql.svg")
@Getter
@Setter
public class HSqlConnector extends HBaseConnector implements IHConnector {

  @HWidgetElement(
      order = "10000-databaseConnectionName",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      comboSource = HComboSource.METADATA,
      metadataKey = "hopper-database-connection",
      label = "Database connection",
      toolTip = "Name of a hopper-database-connection metadata element")
  @HopMetadataProperty
  private String databaseConnectionName;

  @HWidgetElement(
      order = "10100-sql",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.MULTI_LINE_TEXT,
      multiLineTextHeight = 10,
      label = "SQL",
      toolTip = "SQL query executed against the selected database connection")
  @HopMetadataProperty
  private String sql;

  @JsonIgnore private transient ResultSet resultSet;

  public HSqlConnector() {
    super("SqlConnector");
  }

  public HSqlConnector(String databaseConnectionName, String sql) {
    this();
    this.databaseConnectionName = databaseConnectionName;
    this.sql = sql;
  }

  public HSqlConnector(HSqlConnector c) {
    super(c);
    this.databaseConnectionName = c.databaseConnectionName;
    this.sql = c.sql;
  }

  public HSqlConnector clone() {
    return new HSqlConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    Database database = null;

    try {
      if (databaseConnectionName != null && !databaseConnectionName.isBlank()) {
        HSecurityContext.checkResource(
            HAction.CONNECTION_USE, HResourceRef.connection(databaseConnectionName));
      }
      IHopMetadataSerializer<HDatabaseConnection> serializer =
          dataContext.getMetadataProvider().getSerializer(HDatabaseConnection.class);
      HDatabaseConnection databaseConnection = serializer.load(databaseConnectionName);

      DatabaseMeta databaseMeta = databaseConnection.createDatabaseMeta();
      database =
          new Database(
              new LoggingObject("Database connection '" + databaseConnectionName + "'"),
              dataContext.getVariables(),
              databaseMeta);
      database.connect();

      IRowMeta rowMeta = database.getQueryFields(sql, false);

      return rowMeta;
    } catch (Exception e) {
      throw new HException("Unable to describe output of SQL query", e);
    } finally {
      if (database != null) {
        database.disconnect();
      }
    }
  }

  /**
   * For the sampledata usecase we pass 100 rows with a few interesting data types...
   *
   * @param dataContext the data context to optionally reference (not used here)
   * @throws HException
   */
  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {

    Database database = null;
    try {
      // Resource ACL: connection.use on the named hopper-database-connection
      if (databaseConnectionName != null && !databaseConnectionName.isBlank()) {
        HSecurityContext.checkResource(
            HAction.CONNECTION_USE, HResourceRef.connection(databaseConnectionName));
      }

      IHopMetadataSerializer<HDatabaseConnection> serializer =
          dataContext.getMetadataProvider().getSerializer(HDatabaseConnection.class);
      HDatabaseConnection databaseConnection = serializer.load(databaseConnectionName);

      DatabaseMeta databaseMeta = databaseConnection.createDatabaseMeta();

      database =
          new Database(
              new LoggingObject("Database connection '" + databaseConnectionName + "'"),
              dataContext.getVariables(),
              databaseMeta);
      database.connect();

      resultSet = database.openQuery(sql);
      Object[] row = database.getRow(resultSet);
      while (row != null) {
        passToRowListeners(database.getReturnRowMeta(), row);
        row = database.getRow(resultSet);
      }
      database.closeQuery(resultSet);

      // Signal to all row listeners (and subsequent connectors) that no more rows are forthcoming .
      //
      outputDone();

    } catch (HException e) {
      // Row listeners (e.g. crosstab aggregation) throw HException mid-stream — keep the
      // original message so editors show the real cause, not only "Couldn't stream data…".
      throw e;
    } catch (Exception e) {
      throw new HException(
          "Couldn't stream data from database connection " + databaseConnectionName, e);
    } finally {
      if (database != null) {
        database.disconnect();
      }
    }
  }

  @Override
  public void waitUntilFinished() throws HException {
    // StartStreaming works synchronized, no need to get complicated about it
  }

  @Override
  protected void enrichConnectorRun(HConnectorRun run, IDataContext dataContext) {
    run.setDatabaseConnectionName(databaseConnectionName);
    String resolvedSql = sql;
    if (dataContext != null && dataContext.getVariables() != null && sql != null) {
      resolvedSql = dataContext.getVariables().resolve(sql);
    }
    // Cap stored statement text for audit payloads
    if (resolvedSql != null && resolvedSql.length() > 4000) {
      run.setStatementText(resolvedSql.substring(0, 4000) + "…");
    } else {
      run.setStatementText(resolvedSql);
    }
    run.setStatementFingerprint(HExecutionTrace.fingerprintStatement(resolvedSql));
  }
}
