package org.hopper.presentation.connector.types.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hopper.core.HEnvironment;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.GuiFormField;
import org.hopper.core.gui.form.GuiFormFieldType;
import org.hopper.core.gui.form.GuiFormSchema;
import org.hopper.core.gui.form.GuiFormSchemaBuilder;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.csv.HCsvConnector.CsvField;
import org.hopper.presentation.datacontext.IDataContext;

class HCsvConnectorTest {

  @TempDir Path tempDir;

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void streamsRowsWithHeaderAndTypes() throws Exception {
    Path csv = tempDir.resolve("sales.csv");
    Files.writeString(
        csv,
        """
        id,name,amount,active
        1,Alice,12.50,true
        2,Bob,1000,false
        3,Carol,3.14,yes
        """,
        StandardCharsets.UTF_8);

    HCsvConnector connector = new HCsvConnector();
    connector.setFilename(csv.toUri().toString());
    connector.setHeaderPresent(true);
    connector.setSeparator(",");
    connector.setEncoding("UTF-8");
    connector.setFields(
        List.of(
            new CsvField("id", "Integer"),
            new CsvField("name", "String"),
            new CsvField("amount", "Number"),
            new CsvField("active", "Boolean")));

    AtomicInteger rows = new AtomicInteger();
    AtomicBoolean done = new AtomicBoolean(false);
    List<Object[]> data = new ArrayList<>();

    connector.addRowListener(
        (meta, row) -> {
          if (meta != null && row != null) {
            rows.incrementAndGet();
            data.add(row.clone());
          }
          if (meta == null && row == null) {
            done.set(true);
          }
        });

    connector.startStreaming(dataContext(connector));
    assertTrue(done.get());
    assertEquals(3, rows.get());
    assertEquals(1L, data.get(0)[0]);
    assertEquals("Alice", data.get(0)[1]);
    assertEquals(12.5d, (Double) data.get(0)[2], 0.0001);
    assertEquals(Boolean.TRUE, data.get(0)[3]);
  }

  @Test
  void limitStopsAfterNDataRows() throws Exception {
    Path csv = tempDir.resolve("limited.csv");
    Files.writeString(
        csv,
        """
        id,name
        1,a
        2,b
        3,c
        4,d
        5,e
        """,
        StandardCharsets.UTF_8);

    HCsvConnector connector = new HCsvConnector();
    connector.setFilename(csv.toUri().toString());
    connector.setHeaderPresent(true);
    connector.setLimit(2);
    connector.setFields(List.of(new CsvField("id", "Integer"), new CsvField("name", "String")));

    AtomicInteger rows = new AtomicInteger();
    AtomicBoolean done = new AtomicBoolean(false);
    connector.addRowListener(
        (meta, row) -> {
          if (meta != null && row != null) {
            rows.incrementAndGet();
          }
          if (meta == null && row == null) {
            done.set(true);
          }
        });

    connector.startStreaming(dataContext(connector));
    assertTrue(done.get());
    assertEquals(2, rows.get());
  }

  @Test
  void describeOutputMatchesFields() throws Exception {
    HCsvConnector connector = new HCsvConnector();
    connector.setFields(
        List.of(new CsvField("a", "String"), new CsvField("b", "Integer")));
    IRowMeta meta = connector.describeOutput(dataContext(connector));
    assertEquals(2, meta.size());
    assertEquals("a", meta.getValueMeta(0).getName());
    assertEquals("Integer", meta.getValueMeta(1).getTypeDesc());
  }

  @Test
  void detectLayoutInfersTypesAndHeader() throws Exception {
    Path csv = tempDir.resolve("detect.csv");
    Files.writeString(
        csv,
        """
        code,qty,price,flag
        A1,10,1.5,true
        B2,20,2.0,false
        """,
        StandardCharsets.UTF_8);

    HCsvConnector connector = new HCsvConnector();
    connector.setFilename(csv.toUri().toString());
    connector.setHeaderPresent(true);
    connector.setSeparator(",");

    List<CsvField> fields = connector.detectLayout(Variables.getADefaultVariableSpace());
    assertEquals(4, fields.size());
    assertEquals("code", fields.get(0).getName());
    assertEquals("String", fields.get(0).getType());
    assertEquals("qty", fields.get(1).getName());
    assertEquals("Integer", fields.get(1).getType());
    assertEquals("price", fields.get(2).getName());
    assertEquals("Number", fields.get(2).getType());
    assertEquals("flag", fields.get(3).getName());
    assertEquals("Boolean", fields.get(3).getType());
  }

  @Test
  void detectLayoutWithoutHeaderUsesSyntheticNames() throws Exception {
    Path csv = tempDir.resolve("noheader.csv");
    Files.writeString(csv, "1,two,3.0\n4,five,6.0\n", StandardCharsets.UTF_8);

    HCsvConnector connector = new HCsvConnector();
    connector.setFilename(csv.toUri().toString());
    connector.setHeaderPresent(false);
    connector.setSeparator(",");

    List<CsvField> fields = connector.detectLayout(Variables.getADefaultVariableSpace());
    assertEquals(3, fields.size());
    assertEquals("field1", fields.get(0).getName());
    assertEquals("Integer", fields.get(0).getType());
    assertEquals("field2", fields.get(1).getName());
    assertEquals("String", fields.get(1).getType());
    assertEquals("field3", fields.get(2).getName());
    assertEquals("Number", fields.get(2).getType());
  }

  @Test
  void detectLayoutRespectsSemicolonAndGrouping() throws Exception {
    Path csv = tempDir.resolve("eu.csv");
    Files.writeString(
        csv,
        """
        name;amount
        x;1.234,56
        y;2.000,00
        """,
        StandardCharsets.UTF_8);

    HCsvConnector connector = new HCsvConnector();
    connector.setFilename(csv.toUri().toString());
    connector.setHeaderPresent(true);
    connector.setSeparator(";");
    connector.setLocale("de_DE");

    List<CsvField> fields = connector.detectLayout(Variables.getADefaultVariableSpace());
    assertEquals(2, fields.size());
    assertEquals("amount", fields.get(1).getName());
    assertEquals("Number", fields.get(1).getType());

    connector.setFields(fields);
    AtomicInteger rows = new AtomicInteger();
    connector.addRowListener(
        (meta, row) -> {
          if (meta != null && row != null) {
            rows.incrementAndGet();
            if (rows.get() == 1) {
              assertEquals(1234.56d, (Double) row[1], 0.001);
            }
          }
        });
    connector.startStreaming(dataContext(connector));
    assertEquals(2, rows.get());
  }

  @Test
  void columnFormatLengthPrecisionOnValueMeta() throws Exception {
    HCsvConnector connector = new HCsvConnector();
    connector.setLocale("en_US");
    CsvField amount = new CsvField("amount", "Number");
    amount.setFormatMask("#,##0.00");
    amount.setLength("12");
    amount.setPrecision("2");
    connector.setFields(List.of(amount));

    IRowMeta meta = connector.describeOutput(dataContext(connector));
    assertEquals(1, meta.size());
    assertEquals("#,##0.00", meta.getValueMeta(0).getConversionMask());
    assertEquals(12, meta.getValueMeta(0).getLength());
    assertEquals(2, meta.getValueMeta(0).getPrecision());
    assertEquals(".", meta.getValueMeta(0).getDecimalSymbol());
    assertEquals(",", meta.getValueMeta(0).getGroupingSymbol());
  }

  @Test
  void emptyFilenameThrows() {
    HCsvConnector connector = new HCsvConnector();
    connector.setFields(List.of(new CsvField("a", "String")));
    assertThrows(HException.class, () -> connector.startStreaming(dataContext(connector)));
  }

  @Test
  void formSchemaIncludesFilenameFieldsAndDetectButton() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema("CsvConnector");
    assertNotNull(schema);
    assertTrue(schema.isHasPluginWidgets());

    GuiFormField filename = findField(schema, "filename");
    assertNotNull(filename);
    assertEquals(GuiFormFieldType.FILENAME, filename.getType());

    GuiFormField fields = findField(schema, "fields");
    assertNotNull(fields);
    assertEquals(GuiFormFieldType.LIST, fields.getType());
    assertEquals("csvField", fields.getItemKind());

    GuiFormField detect = findField(schema, "detectCsvLayout");
    assertNotNull(detect);
    assertEquals(GuiFormFieldType.BUTTON, detect.getType());
  }

  private static GuiFormField findField(GuiFormSchema schema, String name) {
    return schema.getSections().stream()
        .flatMap(s -> s.getFields().stream())
        .filter(f -> name.equals(f.getFieldName()) || name.equals(f.getId()))
        .findFirst()
        .orElse(null);
  }

  private IDataContext dataContext(HCsvConnector connector) {
    IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();
    return new IDataContext() {
      @Override
      public HConnector getConnector(String name) throws HException {
        return new HConnector(name, connector);
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
  }
}
