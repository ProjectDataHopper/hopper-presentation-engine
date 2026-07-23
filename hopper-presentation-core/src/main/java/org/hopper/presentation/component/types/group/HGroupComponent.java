package org.hopper.presentation.component.types.group;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HAttachment;
import org.hopper.core.HColumn;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.HSortMethod;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.listeners.IDoLayoutListener;
import org.hopper.presentation.component.listeners.IProcessSourceDataListener;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.types.chain.HChainConnector;
import org.hopper.presentation.connector.types.distinct.HDistinctConnector;
import org.hopper.presentation.connector.types.selection.HSelectionConnector;
import org.hopper.presentation.connector.types.sort.HSortConnector;
import org.hopper.presentation.datacontext.ChainDataContext;
import org.hopper.presentation.datacontext.GroupDataContext;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * This is a groupComponent which repeats the given groupComponent for every row in the given
 * connector. Optionally you can select columns, perform sort/distinct
 *
 * <p>First we get the rows for the groups:
 *
 * @see IHComponent#processSourceData(HPresentation, HPage, HComponent, IDataContext,
 *     IRenderContext, HLayoutResults)
 *     <p>Then we calculate the expected size of the composite. Obviously, this size is dynamic so
 *     it's hard to know unless we calculate all the sizes of the groupComponent given the input
 *     data. So we simply read all the data in memory for now and calculate the sizes of all the
 *     expected sizes of all the group elements. That's what we'll return.
 * @see HGroupComponent#getExpectedSize(HPresentation, HPage, HComponent, IDataContext,
 *     IRenderContext, HLayoutResults)
 *     <p>Now we need to spread the groups over the pages. This happens in:
 *     <p>
 * @see IHComponent#doLayout(HPresentation, HPage, HComponent, IDataContext,
 *     IRenderContext, HLayoutResults)
 *     <p>Finally, we render all what we've calculated looping once again over the groups.
 * @see IHComponent#render(HComponentLayoutResult, HLayoutResults, IRenderContext,
 *     HPosition)
 */
@JsonDeserialize(as = HGroupComponent.class)
@HComponentPlugin(
    id = "HGroupComponent",
    name = "Group",
    description = "A way to render another component multiple times creating groups of data",
    image = "ui/images/components/group.svg")
@Getter
@Setter
public class HGroupComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_GROUP_DETAILS = "DATA_GROUP_DETAILS";

  @HWidgetElement(
      order = "10000-columnSelection",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Group columns")
  @HopMetadataProperty
  private List<HColumn> columnSelection;

  @HopMetadataProperty private List<HSortMethod> columnSorts;

  @HWidgetElement(
      order = "10200-distinctSelection",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Distinct selection?")
  @HopMetadataProperty
  private boolean distinctSelection;

  /**
   * Optional explicit join keys for filtering nested connectors. Empty → match group and connector
   * columns by equal name (legacy). Non-empty → only these mappings are applied.
   */
  @HWidgetElement(
      order = "10250-keyMappings",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Nested key mappings",
      toolTip =
          "Optional: map group columns to nested connector columns for filtering. "
              + "Leave empty to match columns by the same name.")
  @HopMetadataProperty
  private List<GroupKeyMapping> keyMappings;

  @HWidgetElement(
      order = "10300-groupComponent",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Group component",
      toolTip = "The nested component rendered once per group key")
  @HopMetadataProperty
  private HComponent groupComponent;

  @HWidgetElement(
      order = "10400-verticalMargin",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Vertical margin between groups")
  @HopMetadataProperty
  private int verticalMargin;

  public HGroupComponent() {
    super("HGroupComponent");

    columnSelection = new ArrayList<>();
    columnSorts = new ArrayList<>();
    keyMappings = new ArrayList<>();
  }

  public HGroupComponent(
      String connectorName,
      List<HColumn> columnSelection,
      List<HSortMethod> columnSorts,
      boolean distinctSelection,
      HComponent groupComponent,
      int verticalMargin) {
    this();
    this.sourceConnectorName = connectorName;
    this.columnSelection = columnSelection;
    this.columnSorts = columnSorts;
    this.distinctSelection = distinctSelection;
    this.groupComponent = groupComponent;
    this.verticalMargin = verticalMargin;
  }

  public HGroupComponent(HGroupComponent c) {
    super("HGroupComponent", c);
    this.sourceConnectorName = c.sourceConnectorName;
    this.columnSelection = new ArrayList<>();
    for (HColumn column : c.columnSelection) {
      this.columnSelection.add(new HColumn(column));
    }
    this.columnSorts = new ArrayList<>();
    for (HSortMethod m : c.columnSorts) {
      this.columnSorts.add(new HSortMethod(m));
    }
    this.distinctSelection = c.distinctSelection;
    this.keyMappings = new ArrayList<>();
    if (c.keyMappings != null) {
      for (GroupKeyMapping m : c.keyMappings) {
        this.keyMappings.add(new GroupKeyMapping(m));
      }
    }
    this.groupComponent = c.groupComponent == null ? null : new HComponent(c.groupComponent);
    this.verticalMargin = c.verticalMargin;
    this.themeName = c.themeName;
  }

  public HGroupComponent clone() {
    return new HGroupComponent(this);
  }

  /**
   * This is the first thing that happens: figure out over what values we need to group over.
   *
   * <p>Connector name, column selection and column sort describes the values over which we need to
   * group We optionally calculate distinct values for the rows.
   *
   * @param presentation
   * @param page
   * @param component
   * @param dataContext
   * @param renderContext
   * @param results
   * @throws HException
   */
  @Override
  public void processSourceData(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    GroupDetails details = new GroupDetails();

    if (org.apache.commons.lang3.StringUtils.isBlank(sourceConnectorName)) {
      results.addDataSet(component, DATA_GROUP_DETAILS, details);
      return;
    }
    HConnector connector = dataContext.getConnector(sourceConnectorName);
    if (connector == null) {
      results.addDataSet(component, DATA_GROUP_DETAILS, details);
      return;
    }

    List<IHConnector> connectors = new ArrayList<>();

    // Select the columns from the data source
    // Sort the columns
    // Get distinct values (optional)
    //
    if (!columnSelection.isEmpty()) {
      connectors.add(new HSelectionConnector(columnSelection));
    }
    if (!columnSorts.isEmpty()) {
      connectors.add(new HSortConnector(columnSelection, columnSorts));
    }
    if (distinctSelection) {
      connectors.add(new HDistinctConnector());
    }

    // Chain the operations
    //
    HConnector selectedConnector;
    IDataContext selectedDataContext;

    if (!connectors.isEmpty()) {
      HChainConnector chain = new HChainConnector(sourceConnectorName, connectors);
      ChainDataContext chainContext = chain.createChainContext(dataContext);
      selectedConnector = chainContext.getLastConnector();
      selectedDataContext = chainContext;
    } else {
      selectedConnector = connector;
      selectedDataContext = dataContext;
    }

    // Get the rows from source and selected, sorted, distinct
    //
    synchronized (selectedConnector.getConnector()) {
      details.rows = selectedConnector.retrieveRows(selectedDataContext);
    }

    // Calculate total size, do call to processRowData of child
    //
    HSize size = new HSize(0, 0);

    // The last component to layout below
    // Null means: first component, keep layout of this component
    //
    HComponent lastComponent = null;

    for (int rowNr = 0; rowNr < details.rows.size(); rowNr++) {

      RowMetaAndData groupRow = details.rows.get(rowNr);
      GroupRowDetails rowDetails = new GroupRowDetails();
      details.rowDetails.add(rowDetails);

      // Make a copy of the current component to store separately in the
      // component geometry map in results.
      //
      String rowComponentName =
          component.getName() + "-group#" + (rowNr + 1) + ":" + groupComponent.getName();

      // Create a new component to render
      //
      HComponent rowComponent = new HComponent(groupComponent);
      rowComponent.setName(rowComponentName);
      IHComponent groupIComponent = rowComponent.getComponent(); // also copied

      groupIComponent.setThemeName(themeName);

      // Copy layout from parent
      //
      rowComponent.setLayout(new HLayout(component.getLayout()));

      // Adjust layout: position from parent to previous row component
      //
      if (lastComponent != null) {
        // This component needs to position below the previous one.
        //
        rowComponent
            .getLayout()
            .setTop(
                new HAttachment(lastComponent.getName(), 0, 0, HAttachment.Alignment.BOTTOM));
      }

      // Create a new data context which will filter the data sources...
      // (optional keyMappings: group column → nested connector column)
      //
      IDataContext groupRowDataContext =
          new GroupDataContext(dataContext, groupRow, keyMappings);

      // Call the processSourceData listeners...
      //
      for (IProcessSourceDataListener listener : rowComponent.getProcessSourceDataListeners()) {
        listener.beforeProcessSourceDataCalled(
            presentation, page, rowComponent, groupRowDataContext, renderContext, results);
      }

      // Read the data for the component (Table, Crosstab, Image, ...)
      // This is stored in groupResults
      //
      groupIComponent.processSourceData(
          presentation, page, rowComponent, groupRowDataContext, renderContext, results);

      // Calculate the expected size.
      // This pre-calculates all sorts of things about the component (table & crosstab cells,
      // heights, widths, ...)
      //
      HSize groupRowExpectedSize =
          groupIComponent.getExpectedSize(
              presentation, page, rowComponent, groupRowDataContext, renderContext, results);
      if (groupRowExpectedSize == null) {
        groupRowExpectedSize = new HSize(0, 0);
      }

      // Add the expected size to the total size
      //
      // For the width we need the maximum
      //
      if (size.getWidth() < groupRowExpectedSize.getWidth()) {
        size.setWidth(groupRowExpectedSize.getWidth());
      }

      // For the height we add up everything...
      //
      size.setHeight(size.getHeight() + groupRowExpectedSize.getHeight() + verticalMargin);

      // Save all these learned facts in the details.
      //
      rowDetails.groupRowDataContext = groupRowDataContext;
      rowDetails.groupExpectedRowSize = groupRowExpectedSize;
      rowDetails.groupRowComponent = rowComponent;

      lastComponent = rowComponent;
    }
    details.size = size;

    // Cache it
    //
    results.addDataSet(component, DATA_GROUP_DETAILS, details);
  }

  @Override
  public HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {
    GroupDetails details = (GroupDetails) results.getDataSet(component, DATA_GROUP_DETAILS);
    return details.size;
  }

  @Override
  public void doLayout(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException {

    // This stores results in the details, including the total size
    //
    HGeometry geometry =
        getExpectedGeometry(presentation, page, component, dataContext, renderContext, results);

    // Get these results back
    //
    GroupDetails details = (GroupDetails) results.getDataSet(component, DATA_GROUP_DETAILS);

    // Call doLayout for every group row
    //
    for (int rowNr = 0; rowNr < details.rowDetails.size(); rowNr++) {
      GroupRowDetails groupRowDetails = details.rowDetails.get(rowNr);
      HComponent rowComponent = groupRowDetails.groupRowComponent;
      IHComponent groupIComponent = rowComponent.getComponent();

      // Call the doLayout listeners...
      //
      for (IDoLayoutListener listener : rowComponent.getDoLayoutListeners()) {
        listener.beforeDoLayout(
          presentation,
          page,
          rowComponent,
          groupRowDetails.groupRowDataContext,
          renderContext,
          results);
      }

      // Do the actual layout
      //
      groupIComponent.doLayout(
          presentation,
          page,
          rowComponent,
          groupRowDetails.groupRowDataContext,
          renderContext,
          results);

      // Lookup the geometry of the last row component. This is the geometry of the last part every
      // time
      // We'll use that as the geometry of the group as a whole (the last part of the group on the
      // last page)
      //
      HGeometry rowComponentGeometry = results.findGeometry(rowComponent.getName());

      // Make the geometry higher to the tune of the vertical margin
      //
      rowComponentGeometry.incHeight(verticalMargin);

      // Now store this under the name of the group
      // It will allow other components to position against this
      //
      results.addComponentGeometry(component.getName(), rowComponentGeometry);
    }
  }

  @Override
  public void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException {

    HComponent component = layoutResult.getComponent();
    HGeometry componentGeometry = layoutResult.getGeometry();
    GroupDetails details = (GroupDetails) results.getDataSet(component, DATA_GROUP_DETAILS);

    // We're not rendering anything here, we let the copies of our group component do that.
    // Start drawing the list of component copies ...
    //
    // Here's where we start on the current page...
    //
    int x = componentGeometry.getX();
    int y = componentGeometry.getY();

    // Loop over the group row details
    //
    for (GroupRowDetails groupRowDetails : details.rowDetails) {
      HSize groupRowSize = groupRowDetails.groupExpectedRowSize;

      // The Layout Results we need to re-create for the group component...
      //
      // HComponentLayoutResult groupLayoutResult = new HComponentLayoutResult(layoutResult);
      // groupLayoutResult.setComponent( groupComponent );
      layoutResult.setComponent(groupComponent);
      layoutResult.getGeometry().setX(x);
      layoutResult.getGeometry().setY(y);
      layoutResult.getGeometry().setWidth(details.size.getWidth());
      layoutResult.getGeometry().setHeight(groupRowSize.getHeight());
      y += groupRowSize.getHeight() + verticalMargin;

      // Render the group component on the parent
      //
      groupComponent.getComponent().render(layoutResult, results, renderContext, offSet);
    }
  }
}
