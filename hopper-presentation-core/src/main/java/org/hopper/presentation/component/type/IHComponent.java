package org.hopper.presentation.component.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.hopper.core.HColorRGB;
import org.hopper.core.HFont;
import org.hopper.core.HGeometry;
import org.hopper.core.HPosition;
import org.hopper.core.HSize;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.page.HPage;
import org.hopper.render.IRenderContext;

/**
 * This interface identifies component type plugin classes. These contain the specific attributes of
 * a component.
 *
 * @author matt
 */
@JsonDeserialize(using = IHComponentDeserializer.class)
@HopMetadataObject(objectFactory = IHComponent.HComponentObjectFactory.class)
public interface IHComponent extends Cloneable {

  /**
   * If a component needs data from a connector, this is where that happens. You can obviously read
   * elsewhere and stub this method if you want to draw on the fly but otherwise, do it here.
   */
  void processSourceData(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException;

  /**
   * First thing a component does: determine its expected size. Calculate the expected size: either
   * the size specified on the component OR the calculated size if not specified and as such dynamic
   */
  HSize getExpectedSize(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException;

  /**
   * Next we calculate the expected geometry of a component based on the specified attachments to
   * other components, relative positions and so on.
   */
  HGeometry getExpectedGeometry(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException;

  /**
   * Perform the layout of this component on the given page of a presentation, modify the results
   * list.
   *
   * @param presentation The presentation to reference
   * @param page The logical page this component is on
   * @param component The component metadata
   * @param dataContext The data context
   * @param renderContext The render context (theme and method to find stable colors)
   * @param results The results of the layouting
   * @throws HException In case something unexpected happened
   */
  void doLayout(
      HPresentation presentation,
      HPage page,
      HComponent component,
      IDataContext dataContext,
      IRenderContext renderContext,
      HLayoutResults results)
      throws HException;

  /** Render the component using the layout results after having done the layout. */
  void render(
      HComponentLayoutResult layoutResult,
      HLayoutResults results,
      IRenderContext renderContext,
      HPosition offSet)
      throws HException;

  @JsonIgnore
  ILogChannel getLogChannel();

  @JsonIgnore
  void setLogChannel(ILogChannel log);

  /**
   * @return a copy of this components metadata
   */
  IHComponent clone();

  /**
   * @return Null if the dialog class is determined automatically. Otherwise returns the dialog
   *     class name.
   */
  String getDialogClassname();

  /**
   * Gets pluginId
   *
   * @return value of pluginId
   */
  String getPluginId();

  /**
   * @param pluginId The pluginId to set
   */
  void setPluginId(String pluginId);

  /**
   * Gets sourceConnectorName
   *
   * @return value of sourceConnectorName
   */
  String getSourceConnectorName();

  /**
   * @param sourceConnectorName The sourceConnectorName to set
   */
  void setSourceConnectorName(String sourceConnectorName);

  /**
   * Gets defaultFont
   *
   * @return value of defaultFont
   */
  HFont getDefaultFont();

  /**
   * @param defaultFont The defaultFont to set
   */
  void setDefaultFont(HFont defaultFont);

  /**
   * Gets defaultColor
   *
   * @return value of defaultColor
   */
  HColorRGB getDefaultColor();

  /**
   * @param defaultColor The defaultColor to set
   */
  void setDefaultColor(HColorRGB defaultColor);

  /**
   * Gets background
   *
   * @return value of background
   */
  boolean isBackground();

  /**
   * @param background The background to set
   */
  void setBackground(boolean background);

  /**
   * Gets backGroundColor
   *
   * @return value of backGroundColor
   */
  HColorRGB getBackGroundColor();

  /**
   * @param backGroundColor The backGroundColor to set
   */
  void setBackGroundColor(HColorRGB backGroundColor);

  /**
   * Gets border
   *
   * @return value of border
   */
  boolean isBorder();

  /**
   * @param border The border to set
   */
  void setBorder(boolean border);

  /**
   * Gets borderColor
   *
   * @return value of borderColor
   */
  HColorRGB getBorderColor();

  /**
   * @param borderColor The borderColor to set
   */
  void setBorderColor(HColorRGB borderColor);

  /**
   * @return The theme to use to render this component
   */
  String getThemeName();

  /**
   * @param themeName The themeName to set
   */
  void setThemeName(String themeName);

  final class HComponentObjectFactory implements IHopMetadataObjectFactory {
    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      if (id == null) {
        return null;
      }
      PluginRegistry registry = PluginRegistry.getInstance();
      IPlugin plugin = registry.getPlugin(HComponentPluginType.class, id);
      if (plugin == null) {
        throw new HopException(
            "Unable to find Hopper component plugin with ID '"
                + id
                + "' in the plugin registry.");
      }
      return registry.loadClass(plugin);
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (object == null) {
        return null;
      }
      return ((IHComponent) object).getPluginId();
    }
  }
}
