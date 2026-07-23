package org.hopper.presentation.connector.types.sampledata;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMetaBuilder;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.datacontext.IDataContext;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize(as = HSampleDataConnector.class)
@HConnectorPlugin(
    id = "SampleDataConnector",
    name = "Sample data",
    description = "A sample data connector giving back a configurable list of sample rows",
    image = "ui/images/connectors/sample-data.svg")
@Getter
@Setter
public class HSampleDataConnector extends HBaseConnector implements IHConnector {

  @HWidgetElement(
      order = "10000-rowCount",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Row count")
  @HopMetadataProperty
  private int rowCount;

  public HSampleDataConnector() {
    this(100);
  }

  public HSampleDataConnector(int rowCount) {
    super("SampleDataConnector");
    this.rowCount = rowCount;
  }

  public HSampleDataConnector(HSampleDataConnector c) {
    super(c);
    this.rowCount = c.rowCount;
  }

  public HSampleDataConnector clone() {
    return new HSampleDataConnector(this);
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) {

    return new RowMetaBuilder()
        .addInteger("id")
        .addString("name")
        .addDate("updated")
        .addBoolean("important")
        .addNumber("random")
        .addString("color")
        .addString("country")
        .build();
  }

  /**
   * For the sampledata usecase we pass 100 rows with a few interesting data types...
   *
   * @param dataContext the data context to optionally reference (not used here)
   * @throws HException
   */
  @Override
  public void startStreaming(IDataContext dataContext) throws HException {

    Random random = new Random(12345678);

    IRowMeta rowMeta = describeOutput(dataContext);

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
    List<String> countries = Arrays.asList("Atlantis", "Sokovia", "Wakanda", "Zamunda");

    long startTime = System.currentTimeMillis();

    for (long id = 1; id <= rowCount; id++) {
      double rnd = random.nextDouble();
      int sillyId = (int) ((random.nextDouble() * id * sillyNames.size())) % sillyNames.size();

      Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
      rowData[0] = id;
      rowData[1] = sillyNames.get(sillyId);
      rowData[2] = new Date(startTime + 1000); // Just to see some change
      rowData[3] = random.nextDouble() > 0.5;
      rowData[4] = random.nextDouble();
      rowData[5] = colors.get((int) Math.round(rnd * 1000) % colors.size());
      rowData[6] = countries.get((int) Math.round(random.nextDouble() * 1000) % countries.size());

      for (IHRowListener rowListener : rowListeners) {
        rowListener.rowReceived(rowMeta, rowData);
      }

      sillyId++;
      if (sillyId >= sillyNames.size()) {
        sillyId = 0;
      }
    }

    // Signal to all row listeners that no more rows are forthcoming.
    //
    outputDone();
  }

  @Override
  public void waitUntilFinished() throws HException {
    // StartStreaming works synchronized, no need to get complicated about it
  }
}
