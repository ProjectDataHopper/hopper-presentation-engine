package org.hopper.presentation.datacontext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.audit.lineage.HExecutionTrace;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.types.group.GroupKeyMapping;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.chain.HChainConnector;
import org.hopper.presentation.connector.types.filter.HSimpleFilterConnector;
import org.hopper.presentation.connector.types.filter.SimpleFilterValue;

/**
 * Data context for one group-key row. Sets variables from group fields and, when nested components
 * request a connector, optionally wraps it with equality filters so detail rows match the group.
 *
 * <p>Filtering rules:
 *
 * <ul>
 *   <li>If {@link #keyMappings} is non-empty: for each mapping, filter {@code connectorColumn =
 *       groupRow[groupColumn]} when both columns exist.
 *   <li>Otherwise (legacy): for each group column whose name also appears on the connector output,
 *       filter that name to the group value.
 * </ul>
 */
@Getter
@Setter
public class GroupDataContext implements IDataContext {

  private final IDataContext parentDataContext;
  private final RowMetaAndData groupRow;
  private final List<GroupKeyMapping> keyMappings;
  private IVariables variableSpace;

  public GroupDataContext(IDataContext parentDataContext, RowMetaAndData groupRow)
      throws HException {
    this(parentDataContext, groupRow, null);
  }

  public GroupDataContext(
      IDataContext parentDataContext,
      RowMetaAndData groupRow,
      List<GroupKeyMapping> keyMappings)
      throws HException {
    this.parentDataContext = parentDataContext;
    this.groupRow = groupRow;
    this.keyMappings =
        keyMappings == null ? Collections.emptyList() : Collections.unmodifiableList(keyMappings);

    variableSpace = new Variables();
    variableSpace.initializeFrom(parentDataContext.getVariables());

    // Set variables with the names of the fields in the group
    //
    for (int i = 0; i < groupRow.getRowMeta().size(); i++) {
      IValueMeta groupRowValue = groupRow.getRowMeta().getValueMetaList().get(i);
      try {
        String value = groupRow.getString(i, "");
        String variable = groupRowValue.getName().replace(" ", "_");

        variableSpace.setVariable(variable, value);
      } catch (HopException e) {
        throw new HException(
            "Error converting group value '" + groupRowValue.getName() + "' to String", e);
      }
    }
  }

  @Override
  public HConnector getConnector(String name) throws HException {

    // The component asks for the connector to read from.
    // We'll look up the connector in the parent.
    //
    HConnector parentConnector = parentDataContext.getConnector(name);
    if (parentConnector == null) {
      // Can't find it, give up immediately
      //
      return null;
    }

    // Copy it for safety
    //
    parentConnector = new HConnector(parentConnector);

    // Now we'll see if any of the columns in the group match the parent connector output
    //
    IRowMeta parentConnectorRowMeta = parentConnector.describeOutput(parentDataContext);

    List<SimpleFilterValue> filterValues =
        buildFilterValues(parentConnectorRowMeta);

    if (!filterValues.isEmpty()) {

      // Create the simple filter connector
      //
      HSimpleFilterConnector simpleFilterConnector = new HSimpleFilterConnector(filterValues);

      // Create a Chain data connector...
      // We give it the same name as the parent connector
      // We can only do this in the data context itself (this class)
      //
      HChainConnector chainConnector =
          new HChainConnector(
              name, Arrays.asList(parentConnector.getConnector(), simpleFilterConnector));
      HConnector connector = new HConnector(name, chainConnector);
      return connector;
    }

    // Simply don't bother filtering, return the parent connector
    //
    return parentConnector;
  }

  /**
   * Build equality filters for the nested connector from the current group row.
   *
   * @param parentConnectorRowMeta output layout of the requested connector
   */
  List<SimpleFilterValue> buildFilterValues(IRowMeta parentConnectorRowMeta) throws HException {
    List<SimpleFilterValue> filterValues = new ArrayList<>();
    if (parentConnectorRowMeta == null) {
      return filterValues;
    }

    if (keyMappings != null && !keyMappings.isEmpty()) {
      // Explicit mappings: groupColumn → connectorColumn
      for (GroupKeyMapping mapping : keyMappings) {
        if (mapping == null) {
          continue;
        }
        String groupCol = StringUtils.trimToNull(mapping.getGroupColumn());
        String connectorCol = StringUtils.trimToNull(mapping.getConnectorColumn());
        if (groupCol == null || connectorCol == null) {
          continue;
        }
        if (groupRow.getRowMeta().searchValueMeta(groupCol) == null) {
          continue;
        }
        if (parentConnectorRowMeta.searchValueMeta(connectorCol) == null) {
          continue;
        }
        try {
          String filterValue = groupRow.getString(groupCol, null);
          filterValues.add(new SimpleFilterValue(connectorCol, filterValue));
        } catch (HopException e) {
          throw new HException(
              "Error converting group row field '" + groupCol + "' to String for key mapping", e);
        }
      }
      return filterValues;
    }

    // Legacy: same column name on group row and connector output
    for (IValueMeta groupValueMeta : groupRow.getRowMeta().getValueMetaList()) {
      IValueMeta parentConnectorValueMeta =
          parentConnectorRowMeta.searchValueMeta(groupValueMeta.getName());
      if (parentConnectorValueMeta != null) {
        String fieldName = groupValueMeta.getName();
        try {
          String filterValue = groupRow.getString(fieldName, null);
          filterValues.add(new SimpleFilterValue(fieldName, filterValue));
        } catch (HopException e) {
          throw new HException(
              "Error converting group row field name '" + fieldName + "' to String", e);
        }
      }
    }
    return filterValues;
  }

  /**
   * Gets variableSpace
   *
   * @return value of variableSpace
   */
  @Override
  public IVariables getVariables() {
    return variableSpace;
  }

  @Override
  public IHopMetadataProvider getMetadataProvider() {
    return parentDataContext.getMetadataProvider();
  }

  @Override
  public HExecutionTrace getExecutionTrace() {
    return parentDataContext != null
        ? parentDataContext.getExecutionTrace()
        : HExecutionTrace.noop();
  }
}
