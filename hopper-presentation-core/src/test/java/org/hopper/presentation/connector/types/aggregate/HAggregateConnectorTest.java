package org.hopper.presentation.connector.types.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HColumn;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFact;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.GuiFormField;
import org.hopper.core.gui.form.GuiFormFieldType;
import org.hopper.core.gui.form.GuiFormSchema;
import org.hopper.core.gui.form.GuiFormSchemaBuilder;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;

class HAggregateConnectorTest {

  @BeforeAll
  static void init() throws Exception {
    HEnvironment.init();
  }

  @Test
  void groupsAndSumsWithSort() throws Exception {
    ListSource source =
        new ListSource(
            rowMeta("color", "qty"),
            Arrays.asList(
                new Object[] {"red", 10L},
                new Object[] {"blue", 3L},
                new Object[] {"red", 5L},
                new Object[] {"blue", 7L},
                new Object[] {"green", 1L}));

    HAggregateConnector agg = new HAggregateConnector();
    agg.setSourceConnectorName("src");
    agg.setGroupColumns(List.of(new HColumn("color")));
    agg.setAggregates(List.of(new HFact("qty", AggregationMethod.SUM)));
    agg.setSortByGroupFields(true);

    List<Object[]> out = new ArrayList<>();
    AtomicInteger done = new AtomicInteger();
    agg.addRowListener(
        (meta, row) -> {
          if (meta != null && row != null) {
            out.add(row.clone());
          }
          if (meta == null && row == null) {
            done.incrementAndGet();
          }
        });

    IDataContext ctx = context(source);
    agg.startStreaming(ctx);
    agg.waitUntilFinished();

    assertEquals(1, done.get());
    assertEquals(3, out.size());
    // Sorted by group field color: blue, green, red
    assertEquals("blue", out.get(0)[0]);
    assertEquals(10L, out.get(0)[1]);
    assertEquals("green", out.get(1)[0]);
    assertEquals(1L, out.get(1)[1]);
    assertEquals("red", out.get(2)[0]);
    assertEquals(15L, out.get(2)[1]);
  }

  @Test
  void countAndAverage() throws Exception {
    ListSource source =
        new ListSource(
            rowMeta("g", "v"),
            Arrays.asList(
                new Object[] {"a", 2.0},
                new Object[] {"a", 4.0},
                new Object[] {"a", 6.0}));

    HAggregateConnector agg = new HAggregateConnector();
    agg.setSourceConnectorName("src");
    agg.setGroupColumns(List.of(new HColumn("g")));
    HFact count = new HFact("v", AggregationMethod.COUNT);
    count.setHeaderValue("cnt");
    HFact avg = new HFact("v", AggregationMethod.AVERAGE);
    avg.setHeaderValue("avg");
    agg.setAggregates(List.of(count, avg));
    agg.setSortByGroupFields(false);

    List<Object[]> out = new ArrayList<>();
    agg.addRowListener(
        (meta, row) -> {
          if (meta != null && row != null) {
            out.add(row.clone());
          }
        });
    agg.startStreaming(context(source));
    agg.waitUntilFinished();

    assertEquals(1, out.size());
    assertEquals("a", out.get(0)[0]);
    assertEquals(3L, out.get(0)[1]);
    assertEquals(4.0d, (Double) out.get(0)[2], 0.0001);
  }

  @Test
  void formSchemaHasGroupAggregatesAndSort() throws Exception {
    GuiFormSchema schema = new GuiFormSchemaBuilder().buildConnectorSchema("AggregateConnector");
    GuiFormField groups = findField(schema, "groupColumns");
    assertTrue(groups != null && groups.getType() == GuiFormFieldType.LIST);
    assertEquals("column", groups.getItemKind());

    GuiFormField facts = findField(schema, "aggregates");
    assertTrue(facts != null && facts.getType() == GuiFormFieldType.LIST);
    assertEquals("fact", facts.getItemKind());

    GuiFormField sort = findField(schema, "sortByGroupFields");
    assertTrue(sort != null && sort.getType() == GuiFormFieldType.CHECKBOX);
  }

  private static GuiFormField findField(GuiFormSchema schema, String name) {
    return schema.getSections().stream()
        .flatMap(s -> s.getFields().stream())
        .filter(f -> name.equals(f.getFieldName()) || name.equals(f.getId()))
        .findFirst()
        .orElse(null);
  }

  private static IRowMeta rowMeta(String group, String value) {
    IRowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString(group));
    if ("v".equals(value)) {
      meta.addValueMeta(new ValueMetaNumber(value));
    } else {
      meta.addValueMeta(new ValueMetaInteger(value));
    }
    return meta;
  }

  private static IDataContext context(ListSource source) {
    IHopMetadataProvider provider = new MemoryMetadataProvider();
    return new IDataContext() {
      @Override
      public HConnector getConnector(String name) throws HException {
        return new HConnector(name, source);
      }

      @Override
      public IVariables getVariables() {
        return Variables.getADefaultVariableSpace();
      }

      @Override
      public IHopMetadataProvider getMetadataProvider() {
        return provider;
      }
    };
  }

  /** Minimal in-memory source for unit tests. */
  static final class ListSource extends HBaseConnector implements IHConnector {
    private final IRowMeta meta;
    private final List<Object[]> rows;

    ListSource(IRowMeta meta, List<Object[]> rows) {
      super("ListSource");
      this.meta = meta;
      this.rows = rows;
    }

    @Override
    public HBaseConnector clone() {
      return this;
    }

    @Override
    public IRowMeta describeOutput(IDataContext dataContext) {
      return meta;
    }

    @Override
    protected void doStartStreaming(IDataContext dataContext) throws HException {
      for (Object[] row : rows) {
        passToRowListeners(meta, row);
      }
      outputDone();
    }

    @Override
    public void waitUntilFinished() {}
  }
}
