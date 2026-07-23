package org.hopper.presentation.component.types.composite;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HAttachment;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HBaseComponent;
import org.hopper.presentation.component.type.HComponentPlugin;
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
 * This is a composite component which groups a bunch of composites The size is the maximum reach of
 * all the components in the composite The composite will have its own data context
 *
 * <p>First we get the rows for all the components in the group:
 *
 * <p>
 *
 * @see IHComponent#processSourceData(HPresentation, HPage, HComponent, IDataContext,
 *     IRenderContext, HLayoutResults)
 *     <p>Then we calculate the expected size of the composite. Obviously, this size is dynamic so
 *     it's hard to know unless we calculate all the sizes of the components given the data context
 *     of the composite.
 *     <p>
 * @see HCompositeComponent#getExpectedSize(HPresentation, HPage, HComponent,
 *     IDataContext, IRenderContext, HLayoutResults)
 *     <p>Now we render all the components in the composite
 *     <p>
 * @see IHComponent#doLayout(HPresentation, HPage, HComponent, IDataContext,
 *     IRenderContext, HLayoutResults)
 *     <p>Finally, have all the child composites render themselves
 *     <p>
 * @see IHComponent#render(HComponentLayoutResult, HLayoutResults, IRenderContext,
 *     HPosition)
 */
@JsonDeserialize(as = HCompositeComponent.class)
@HComponentPlugin(
    id = "HCompositeComponent",
    name = "Composite",
    description = "In this component you can place other components",
    image = "ui/images/components/composite.svg")
@Getter
@Setter
public class HCompositeComponent extends HBaseComponent implements IHComponent {

  public static final String DATA_COMPOSITE_DETAILS = "DATA_COMPOSITE_DETAILS";

  @HWidgetElement(
      order = "10000-children",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Child components",
      toolTip = "Components nested inside this composite")
  @HopMetadataProperty
  private List<HComponent> children;

  public HCompositeComponent() {
    super("HCompositeComponent");
    children = new ArrayList<>();
  }

  public HCompositeComponent(List<HComponent> children) {
    this();
    this.children = children;
  }

  public HCompositeComponent(HCompositeComponent c) {
    super("HCompositeComponent", c);
    this.children = new ArrayList<>();
    for (HComponent child : c.children) {
      this.children.add(new HComponent(child));
    }
  }

  public HCompositeComponent clone() {
    return new HCompositeComponent(this);
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

    CompositeDetails details = new CompositeDetails();

    // Calculate total size, do call to processRowData of children
    //
    HSize size = new HSize(0, 0);

    for (HComponent child : children) {

      ChildDetails childDetails = new ChildDetails();
      details.childDetails.add(childDetails);

      IHComponent childIComponent = child.getComponent();

      // Make a copy of the child component to make sure we don't mess up the original metadata when
      // we calculate
      // relative position versus the composite borders.
      // With this new unique name it's safe to use the composite Layout results objects
      //
      String childComponentName = component.getName() + "-child(" + child.getName() + ")";

      // Create a new component to render
      //
      HComponent childComponent = new HComponent(child);
      childComponent.setName(childComponentName);

      // Pass along the theme
      childIComponent.setThemeName(getThemeName());

      childDetails.childComponent = childComponent;

      // Top references to page means: references to top of composite
      // Bottom references to page means: references to bottom composite which isn't know until
      // after rendering:
      //   We consider this to be nonsensical right now and throw an error
      // Left references to page means: references to left of composite
      // Right references to page (null) means: right of the page
      //

      if (child.getLayout() == null) {
        throw new HException(
            "Please provide a layout for child component '"
                + child.getName()
                + "' in composite component '"
                + component.getName()
                + "'");
      }

      // Copy layout from parent
      //
      HLayout childLayout = new HLayout(child.getLayout());
      childComponent.setLayout(childLayout);

      // Adjust layout: position from page (null) to parent row component
      //
      if (childLayout.getBottom() != null) {
        throw new HException(
            "The bottom of a composite can't be referenced since its size is dynamic and unknown upfront.");
      }
      HLayout componentLayout = component.getLayout();

      // Is the child referencing the top of the page?
      // Reference the top of the composite instead
      //
      HAttachment componentTop = componentLayout.getTop();
      if (componentTop != null) {
        HAttachment childTop = childLayout.getTop();
        if (childTop != null && childTop.getComponentName() == null) {
          childLayout.setTop(
              new HAttachment(
                  componentTop.getComponentName(),
                  childTop.getPercentage(),
                  childTop.getOffset(),
                  componentTop.getAlignment()));
        }
      }
      // Is the child referencing the left of the page?
      // Reference the left side of the composite instead
      //
      HAttachment componentLeft = componentLayout.getLeft();
      if (componentLeft != null) {
        HAttachment childLeft = childLayout.getLeft();
        if (childLeft != null && childLeft.getComponentName() == null) {
          childLayout.setLeft(
              new HAttachment(
                  componentLeft.getComponentName(),
                  childLeft.getPercentage(),
                  childLeft.getOffset(),
                  componentLeft.getAlignment()));
        }
      }
      // Is the child referencing the right of the page?
      // Reference the right side of the composite instead
      //
      HAttachment componentRight = componentLayout.getRight();
      if (componentRight != null) {
        HAttachment childRight = childLayout.getRight();
        if (childRight != null && childRight.getComponentName() == null) {
          childLayout.setRight(
              new HAttachment(
                  componentRight.getComponentName(),
                  childRight.getPercentage(),
                  childRight.getOffset(),
                  componentRight.getAlignment()));
        }
      }

      // Read the data for the component (Table, Crosstab, Image, ...)
      // This is stored in childLayoutResults
      //
      childIComponent.processSourceData(
          presentation, page, childComponent, dataContext, renderContext, results);

      // Calculate the expected size.
      // This pre-calculates all sorts of things about the component (table & crosstab cells,
      // heights, widths, ...)
      //
      HSize childExpectedSize =
          childIComponent.getExpectedSize(
              presentation, page, childComponent, dataContext, renderContext, results);

      // Save all these learned size in the details (mostly for debugging).
      //
      childDetails.childExpectedSize = childExpectedSize;
    }

    // We can't simply change the name.  Make sure all references in the other children are updated!
    //
    for (int x = 0; x < children.size(); x++) {
      String oldName = children.get(x).getName();
      String newName = details.childDetails.get(x).childComponent.getName();

      for (int i = 0; i < details.childDetails.size(); i++) {
        HComponent childCopy = details.childDetails.get(i).childComponent;
        childCopy.getLayout().replaceReferences(oldName, newName);
      }
    }

    // We can't calculate the size until we do the layout of the composite children
    //
    details.size = new HSize(0, 0);

    // Cache it
    //
    results.addDataSet(component, DATA_COMPOSITE_DETAILS, details);
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
      return null;
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

    // Get these results back
    //
    CompositeDetails details =
        (CompositeDetails) results.getDataSet(component, DATA_COMPOSITE_DETAILS);

    HGeometry compositeGeometry = new HGeometry(0, 0, 0, 0);

    int previousPageNr = results.getCurrentRenderPage(page).getPageNumber();

    // Here we can simply do the layout of every child
    //
    for (int i = 0; i < details.childDetails.size(); i++) {
      ChildDetails childDetails = details.childDetails.get(i);

      HComponent childComponent = childDetails.childComponent;
      IHComponent childIComponent = childComponent.getComponent();
      childIComponent.doLayout(
          presentation, page, childComponent, dataContext, renderContext, results);

      // If we passed onto a new page, we need to keep the lowest on that page
      // So we start again at the top...
      //
      int pageNr = results.getCurrentRenderPage(page).getPageNumber();
      if (pageNr != previousPageNr) {
        compositeGeometry = new HGeometry(0, 0, 0, 0);
      }

      // Grab the geometry from the results, we need it to calculate the total surface of the
      // composite.
      //
      HGeometry childGeometry = results.findGeometry(childComponent.getName());

      // Compute the lowest surface area of this composite
      //
      compositeGeometry.lowest(childGeometry);

      previousPageNr = pageNr;
    }

    // Save the total composite geometry also in the results
    //
    results.addComponentGeometry(component.getName(), compositeGeometry);
    details.size = new HSize(compositeGeometry.getWidth(), compositeGeometry.getHeight());
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
    CompositeDetails details =
        (CompositeDetails) results.getDataSet(component, DATA_COMPOSITE_DETAILS);

    for (int i = 0; i < children.size(); i++) {
      HComponent child = children.get(i);
      IHComponent childIComponent = child.getComponent();
      ChildDetails childDetails = details.childDetails.get(i);

      HGeometry childGeometry = results.findGeometry(childDetails.childComponent.getName());

      HComponentLayoutResult childComponentLayoutResult =
          new HComponentLayoutResult(layoutResult);
      childComponentLayoutResult.setComponent(child);
      childComponentLayoutResult.setGeometry(childGeometry);

      childIComponent.render(childComponentLayoutResult, results, renderContext, offSet);
    }
  }
}
