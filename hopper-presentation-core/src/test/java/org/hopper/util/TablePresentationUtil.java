package org.hopper.util;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.core.HColorRGB;
import org.hopper.core.HColumn;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HSortMethod;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.types.chain.HChainConnector;
import org.hopper.presentation.connector.types.distinct.HDistinctConnector;
import org.hopper.presentation.connector.types.passthrough.HPassthroughConnector;
import org.hopper.presentation.connector.types.selection.HSelectionConnector;
import org.hopper.presentation.connector.types.sort.HSortConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;

import java.awt.*;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class TablePresentationUtil extends BasePresentationUtil {

  public static final String CONNECTOR_NAME_SQL = "SQL rows";
  public static final String COMPONENT_NAME_LABEL = "Label1";
  public static final String CONNECTOR_NAME_PASSTHROUGH = "PassThrough";
  private static final String COMPONENT_NAME_TABLE = "Table1";
  private static final String CONNECTOR_NAME_CHAIN = "Chain";

  public TablePresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    super(metadataProvider, variables);
  }

  public static final HDatabaseConnection populateTestTable(
      IVariables variables, String tableName, int rowCount) throws Exception {

    // Create a local H2 database in some tmp space.
    //
    String dbFolder = System.getProperty("java.io.tmpdir", ".") + Const.FILE_SEPARATOR + "testDb";
    HDatabaseConnection connection =
        new HDatabaseConnection("testDb", "H2", null, null, dbFolder, null, null);
    DatabaseMeta databaseMeta = connection.createDatabaseMeta();
    Database database =
        new Database(new LoggingObject(connection.getName()), variables, databaseMeta);

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("id"));
    rowMeta.addValueMeta(new ValueMetaString("name", 500, -1 ));
    rowMeta.addValueMeta(new ValueMetaDate("updated"));
    rowMeta.addValueMeta(new ValueMetaBoolean("important"));
    rowMeta.addValueMeta(new ValueMetaNumber("random", 9, 4));
    rowMeta.addValueMeta(new ValueMetaString("color", 20, -1));

    try {

      database.connect();

      // Drop the table...
      //
      try {
        database.execStatement("DROP TABLE " + tableName);
      } catch (Exception e) {
        // Ignore error
      }

      // Create the table for the columns...
      //
      String sql = database.getCreateTableStatement(tableName, rowMeta, null, false, null, false);
      database.execStatement(sql);

      // Some names to spread around.
      //
      List<String> sillyNames =
          Arrays.asList(
              "Adam Zapel",
              "Ali Gaither",
              "Anna Conda",
              "Anne Teak",
              "Barb Dwyer",
              "Bonnie Ann Clyde",
              "Candace Spencer",
              "Doug Hole",
              "Earl Lee Riser",
              "Kent C. Strait",
              "Jed I Knight",
              "Bug Light",
              "Chris P. Bacon",
              "Ken Hurt",
              "Ben Dover",
              "Dixie Normous",
              "Justin Slider",
              "Mike Litoris");

      List<String> colors = Arrays.asList("Red", "Green", "Blue");

      long startTime = System.currentTimeMillis();

      database.prepareInsert(rowMeta, tableName);
      PreparedStatement prepStatementInsert = database.getPrepStatementInsert();

      // Put some random rows into the table...
      //
      Random random = new Random(12345678);
      int sillyId = 0;
      for (long id = 1; id <= rowCount; id++) {
        Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
        double rnd = random.nextDouble();

        rowData[0] = id;
        rowData[1] = sillyNames.get(sillyId);
        rowData[2] = new Date(startTime + 1000); // Just to see some change
        rowData[3] = rnd > 0.5;
        rowData[4] = rnd * (id * 2 / rowCount);
        rowData[5] = colors.get((int) Math.round(rnd * 1000) % colors.size());

        database.setValuesInsert(rowMeta, rowData);
        database.insertRow();

        sillyId++;
        if (sillyId >= sillyNames.size()) {
          sillyId = 0;
        }
      }
      database.closeInsert();

      return connection;
    } finally {
      database.disconnect();
    }
  }

  public HPresentation createTablePresentation(int nr) throws Exception {

    HPresentation presentation =
        createBasePresentation(
            "Table (" + nr + ")",
            "Table " + nr + " description",
            100,
            "A table with a label right below that",
            true);

    HPage pageOne = presentation.getPages().get(0);

    IHopMetadataSerializer<HDatabaseConnection> serializer =
        metadataProvider.getSerializer(HDatabaseConnection.class);

    // Create a table and put a bunch of rows in it...
    //
    String tableName = "test_table";
    int rowCount = 100;
    HDatabaseConnection connection =
        TablePresentationUtil.populateTestTable(variables, tableName, rowCount);
    serializer.save(connection);

    HDatabaseConnection steelWheels =
        H2DatabaseUtil.createSteelWheelsDatabase(metadataProvider, variables);

    // Get 100 rows in the output.
    //
    IHConnector sqlSource =
        new HSqlConnector(connection.getName(), "SELECT * FROM " + tableName);
    HConnector source = new HConnector(CONNECTOR_NAME_SQL, sqlSource);
    saveConnector(source);

    IHConnector passThrough = new HPassthroughConnector(source.getName());
    HConnector passThroughConnector = new HConnector(CONNECTOR_NAME_PASSTHROUGH, passThrough);
    saveConnector(passThroughConnector);

    List<HColumn> columnSelection =
        Arrays.asList(
            new HColumn("id", "ID", HHorizontalAlignment.RIGHT, HVerticalAlignment.TOP),
            new HColumn("name", "Name", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP),
            new HColumn(
                "updated",
                "Time of update",
                HHorizontalAlignment.LEFT,
                HVerticalAlignment.TOP),
            new HColumn(
                "important", "Imp?", HHorizontalAlignment.CENTER, HVerticalAlignment.TOP),
            new HColumn(
                "color", "Color", HHorizontalAlignment.CENTER, HVerticalAlignment.TOP),
            new HColumn(
                "random", "Random Nr.", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP));

    columnSelection.get(0).setFormatMask("#");
    columnSelection.get(2).setFormatMask("yyyy/MM/dd HH:mm:ss");
    columnSelection.get(4).setFormatMask("0.0000");

    HTableComponent table =
        new HTableComponent(passThroughConnector.getName(), columnSelection);
    table.setBorder(false);
    table.setHorizontalMargin(4);
    table.setVerticalMargin(2);
    table.setDefaultColor(new HColorRGB(80, 80, 80));
    table.setBorderColor(new HColorRGB(120, 120, 120));
    table.setBackground(false);
    table.setBackGroundColor(new HColorRGB(220, 220, 220));
    table.setGridColor(new HColorRGB(180, 180, 180));
    table.setDefaultFont(new HFont(Font.MONOSPACED, "14", false, false));
    table.setHeaderFont(new HFont("Arial", "16", true, false));
    table.setEvenHeights(true);
    table.setHeader(true);
    table.setHeaderOnEveryPage(true);

    HComponent table1 = new HComponent(COMPONENT_NAME_TABLE, table);
    HLayout tableLayout = new HLayout(0, 0);
    table1.setLayout(tableLayout);
    pageOne.getComponents().add(table1);

    HLabelComponent label = new HLabelComponent();
    label.setLabel("<123_ö gpĨ\"456>");
    label.setDefaultFont(new HFont("Courier", "48", false, false));
    label.setHorizontalAlignment(HHorizontalAlignment.CENTER);
    label.setVerticalAlignment(HVerticalAlignment.TOP);
    label.setBorder(true);
    label.setDefaultColor(new HColorRGB(0, 140, 194));
    label.setBorderColor(new HColorRGB(80, 80, 80));
    label.setBackGroundColor(new HColorRGB(200, 200, 200));

    HComponent label1 = new HComponent(COMPONENT_NAME_LABEL, label);
    label1.setLayout(
        new HLayoutBuilder().left().right().topFromBottom(COMPONENT_NAME_TABLE, 0, 30).build());

    pageOne.getComponents().add(label1);

    return presentation;
  }

  public HPresentation createTableChainPresentation(int nr) throws Exception {
    HPresentation presentation = createTablePresentation(nr);

    // Let's modify the presentation
    //
    // - Select only a few fields
    // - Sort the rows
    // - Get distinct values
    //

    // Selection
    //
    List<HColumn> columns = Arrays.asList(new HColumn("color"), new HColumn("important"));
    HSelectionConnector selection = new HSelectionConnector(columns);

    // Sort
    //
    List<HSortMethod> sorts =
        Arrays.asList(
            new HSortMethod(HSortMethod.Type.NATIVE_VALUE, true),
            new HSortMethod(HSortMethod.Type.NATIVE_VALUE, true));
    HSortConnector sort = new HSortConnector(columns, sorts);

    // Distinct
    //
    HDistinctConnector distinct = new HDistinctConnector();

    // Use a Chain to test them all at once.
    //
    HChainConnector chain = new HChainConnector();

    // Read from the pass through connector
    //
    chain.setSourceConnectorName(CONNECTOR_NAME_PASSTHROUGH);
    chain.setConnectors(Arrays.asList(selection, sort, distinct));

    HConnector chainConnector = new HConnector(CONNECTOR_NAME_CHAIN, chain);
    saveConnector(chainConnector);

    // Modify the Table component to read from the chain
    //
    HPage pageOne = presentation.getPages().get(0);
    HComponent tableComponent = pageOne.findComponent(COMPONENT_NAME_TABLE);
    tableComponent.getComponent().setSourceConnectorName(CONNECTOR_NAME_CHAIN);

    // Only show the 2 remaining columns
    //
    HTableComponent table = (HTableComponent) tableComponent.getComponent();
    table.setColumnSelection(
        Arrays.asList(
            new HColumn(
                "color", "Color", HHorizontalAlignment.LEFT, HVerticalAlignment.TOP),
            new HColumn(
                "important", "Imp?", HHorizontalAlignment.CENTER, HVerticalAlignment.TOP)));

    // Remove the label
    //
    pageOne.getComponents().remove(pageOne.findComponent(COMPONENT_NAME_LABEL));

    return presentation;
  }
}
