package org.hopper.presentation.connector.types.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.HEnvironment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.util.BasePresentationUtil;
import org.hopper.util.TablePresentationUtil;

class HSqlConnectorTest {

  private static final int ROW_COUNT = 50;
  private static final String TABLE_NAME = "SQL_TEST_TABLE";

  private IHopMetadataProvider metadataProvider;
  private IVariables variables;
  private HDatabaseConnection connection;

  @BeforeEach
  void setUp() throws Exception {
    metadataProvider = new MemoryMetadataProvider();
    variables = Variables.getADefaultVariableSpace();
    HEnvironment.init();
    BasePresentationUtil.registerTestPlugins();

    IHopMetadataSerializer<HDatabaseConnection> dbSerializer =
        metadataProvider.getSerializer(HDatabaseConnection.class);
    connection = TablePresentationUtil.populateTestTable(variables, TABLE_NAME, ROW_COUNT);
    dbSerializer.save(connection);
  }

  @Test
  void streamsAllRowsFromSqlQuery() throws Exception {
    String sql = "SELECT * FROM " + TABLE_NAME;
    final HSqlConnector hopperSqlConnector = new HSqlConnector(connection.getName(), sql);

    AtomicInteger rowCounter = new AtomicInteger(0);
    AtomicBoolean endReceived = new AtomicBoolean(false);

    hopperSqlConnector.addRowListener(
        (rowMeta, rowData) -> {
          if (rowMeta != null && rowData != null) {
            rowCounter.incrementAndGet();
          }
          if (rowMeta == null && rowData == null) {
            endReceived.set(true);
          }
        });

    IDataContext dataContext =
        new IDataContext() {
          @Override
          public HConnector getConnector(String name) throws HException {
            return new HConnector(name, hopperSqlConnector);
          }

          @Override
          public IVariables getVariables() {
            return Variables.getADefaultVariableSpace();
          }

          @Override
          public IHopMetadataProvider getMetadataProvider() {
            return metadataProvider;
          }
        };

    hopperSqlConnector.startStreaming(dataContext);
    hopperSqlConnector.waitUntilFinished();

    assertTrue(endReceived.get());
    assertEquals(ROW_COUNT, rowCounter.get());
  }
}
