package org.hopper.presentation.datacontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.component.types.group.GroupKeyMapping;
import org.hopper.presentation.connector.ConnectorTestSupport;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.filter.SimpleFilterValue;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;

class GroupDataContextTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
  }

  private static RowMetaAndData groupRow(String col, String value) {
    RowMeta meta = new RowMeta();
    meta.addValueMeta(new ValueMetaString(col));
    return new RowMetaAndData(meta, new Object[] {value});
  }

  private static IRowMeta connectorMeta(String... columns) {
    RowMeta meta = new RowMeta();
    for (String c : columns) {
      meta.addValueMeta(new ValueMetaString(c));
    }
    return meta;
  }

  @Test
  void legacySameNameMatch() throws Exception {
    HConnector sample =
        ConnectorTestSupport.wrap("Sample Data", new HSampleDataConnector(5));
    PresentationDataContext parent = ConnectorTestSupport.dataContext(sample);
    GroupDataContext ctx = new GroupDataContext(parent, groupRow("country", "Belgium"));

    List<SimpleFilterValue> filters = ctx.buildFilterValues(connectorMeta("country", "name"));
    assertEquals(1, filters.size());
    assertEquals("country", filters.get(0).getFieldName());
    assertEquals("Belgium", filters.get(0).getFilterValue());
  }

  @Test
  void explicitMappingDifferentNames() throws Exception {
    HConnector sample =
        ConnectorTestSupport.wrap("Sample Data", new HSampleDataConnector(5));
    PresentationDataContext parent = ConnectorTestSupport.dataContext(sample);
    List<GroupKeyMapping> mappings =
        Collections.singletonList(new GroupKeyMapping("country", "Country"));
    GroupDataContext ctx =
        new GroupDataContext(parent, groupRow("country", "France"), mappings);

    // Nested connector has "Country" not "country"
    List<SimpleFilterValue> filters = ctx.buildFilterValues(connectorMeta("id", "Country", "name"));
    assertEquals(1, filters.size());
    assertEquals("Country", filters.get(0).getFieldName());
    assertEquals("France", filters.get(0).getFilterValue());
  }

  @Test
  void explicitMappingSkipsMissingColumns() throws Exception {
    HConnector sample =
        ConnectorTestSupport.wrap("Sample Data", new HSampleDataConnector(5));
    PresentationDataContext parent = ConnectorTestSupport.dataContext(sample);
    List<GroupKeyMapping> mappings =
        Arrays.asList(
            new GroupKeyMapping("country", "Country"),
            new GroupKeyMapping("missing", "x"),
            new GroupKeyMapping("country", "alsoMissing"));
    GroupDataContext ctx =
        new GroupDataContext(parent, groupRow("country", "Spain"), mappings);

    List<SimpleFilterValue> filters = ctx.buildFilterValues(connectorMeta("Country", "name"));
    assertEquals(1, filters.size());
    assertEquals("Country", filters.get(0).getFieldName());
    assertEquals("Spain", filters.get(0).getFilterValue());
  }

  @Test
  void emptyMappingsFallsBackToSameName() throws Exception {
    HConnector sample =
        ConnectorTestSupport.wrap("Sample Data", new HSampleDataConnector(5));
    PresentationDataContext parent = ConnectorTestSupport.dataContext(sample);
    GroupDataContext ctx =
        new GroupDataContext(parent, groupRow("country", "Italy"), Collections.emptyList());

    List<SimpleFilterValue> filters = ctx.buildFilterValues(connectorMeta("country"));
    assertEquals(1, filters.size());
    assertEquals("country", filters.get(0).getFieldName());
  }

  @Test
  void noMatchingColumnsMeansNoFilters() throws Exception {
    HConnector sample =
        ConnectorTestSupport.wrap("Sample Data", new HSampleDataConnector(5));
    PresentationDataContext parent = ConnectorTestSupport.dataContext(sample);
    GroupDataContext ctx = new GroupDataContext(parent, groupRow("country", "Germany"));

    List<SimpleFilterValue> filters = ctx.buildFilterValues(connectorMeta("id", "name"));
    assertTrue(filters.isEmpty());
  }

  @Test
  void getConnectorAppliesFilterChain() throws Exception {
    HConnector sample =
        ConnectorTestSupport.wrap("Sample Data", new HSampleDataConnector(40));
    PresentationDataContext parent = ConnectorTestSupport.dataContext(sample);
    // Sample data countries: Atlantis, Sokovia, Wakanda, Zamunda
    GroupDataContext ctx = new GroupDataContext(parent, groupRow("country", "Atlantis"));

    HConnector filtered = ctx.getConnector("Sample Data");
    List<?> rows = filtered.retrieveRows(ctx);
    assertTrue(rows.size() > 0);
    assertTrue(rows.size() < 40, "Filter should drop non-Atlantis rows, got " + rows.size());
  }
}
