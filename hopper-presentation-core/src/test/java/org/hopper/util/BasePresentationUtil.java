package org.hopper.util;

import org.apache.hop.core.database.DatabaseMetaPlugin;
import org.apache.hop.core.database.DatabasePluginType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.databases.h2.H2DatabaseMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.core.HAttachment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.svg.HSvgComponent;
import org.hopper.presentation.component.types.svg.ScaleType;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;

public class BasePresentationUtil {

  public static final String HEADER_MESSAGE_LABEL = "HeaderLabel";
  public static String CONNECTOR_SAMPLE_ROWS = "Sample rows";
  protected IHopMetadataProvider metadataProvider;
  protected IVariables variables;

  public BasePresentationUtil(IHopMetadataProvider metadataProvider, IVariables variables) {
    this.metadataProvider = metadataProvider;
    this.variables = variables;
  }

  public static void registerTestPlugins() throws HopPluginException {
    PluginRegistry.getInstance()
        .registerPluginClass(
            H2DatabaseMeta.class.getName(), DatabasePluginType.class, DatabaseMetaPlugin.class);
  }

  /**
   * Save a connector into the test metadata catalog so presentations can resolve it by name.
   */
  protected void saveConnector(HConnector connector) throws HException {
    try {
      metadataProvider.getSerializer(HConnector.class).save(connector);
    } catch (HopException e) {
      throw new HException("Error saving test connector " + connector.getName(), e);
    }
  }

  /** Ensure the default theme exists in the metadata catalog. */
  protected void ensureDefaultTheme() throws HException {
    try {
      HTheme theme = HTheme.getDefault();
      if (metadataProvider.getSerializer(HTheme.class).load(theme.getName()) == null) {
        metadataProvider.getSerializer(HTheme.class).save(theme);
      }
    } catch (HopException e) {
      throw new HException("Error saving default theme", e);
    }
  }

  protected HPresentation createBasePresentation(
      String name, String description, int rowCount, String headerMessage) throws HException {
    return createBasePresentation(name, description, rowCount, headerMessage, false);
  }

  protected HPresentation createBasePresentation(
      String name, String description, int rowCount, String headerMessage, boolean portrait)
      throws HException {
    HPresentation presentation = new HPresentation();
    presentation.setName(name);
    presentation.setDescription(description);

    // Default theme lives in the catalog; presentation only stores the name.
    ensureDefaultTheme();
    HTheme theme = HTheme.getDefault();
    presentation.setDefaultThemeName(theme.getName());

    addHeaderFooter(presentation, headerMessage, portrait);

    // Create a one-page document
    //
    HPage pageOne = HPage.getA4(portrait);
    presentation.getPages().add(pageOne);

    // Sample connector in metadata (not embedded on the presentation)
    //
    IHConnector sampleRowsConnector = new HSampleDataConnector(rowCount);
    HConnector sampleRows = new HConnector(CONNECTOR_SAMPLE_ROWS, sampleRowsConnector);
    saveConnector(sampleRows);

    return presentation;
  }

  protected static void addHeaderFooter(
      HPresentation presentation, String headerMessage, boolean portrait) {
    // Add a header with a logo at the top of the page
    //
    HPage header = HPage.getHeaderFooter(true, portrait, 50);
    header.getComponents().add(createHeaderLabelComponent(headerMessage));
    header.getComponents().add(createPresentationNameLabelComponent());
    presentation.setHeader(header);

    // Add a footer with a single label at the bottom of the page.
    //
    HPage footer = HPage.getHeaderFooter(false, portrait, 25);
    footer.getComponents().add(createPageNumberLabelComponent());
    footer.getComponents().add(createSysdateLabelComponent());
    footer.getComponents().add(createLogoComponent());

    presentation.setFooter(footer);
  }

  protected static HComponent createLogoComponent() {
    HSvgComponent hopperLabel = new HSvgComponent("hopper-presentation-logo.svg", ScaleType.MIN);
    hopperLabel.setBorder(true);
    HComponent imageComponent = new HComponent("Logo", hopperLabel);
    imageComponent.setLayout(new HLayoutBuilder().left(50, 0).top(10).bottom(0).build());
    return imageComponent;
  }

  protected static HComponent createHeaderLabelComponent(String headerMessage) {
    HLabelComponent label = new HLabelComponent();
    label.setLabel(headerMessage);
    label.setBorder(false);
    HComponent labelComponent = new HComponent(HEADER_MESSAGE_LABEL, label);
    HLayout labelLayout = new HLayout();
    labelLayout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
    labelLayout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.TOP));
    labelComponent.setLayout(labelLayout);
    return labelComponent;
  }

  protected static HComponent createPresentationNameLabelComponent() {
    HLabelComponent label = new HLabelComponent();
    label.setLabel("${PRESENTATION_NAME}");
    label.setBorder(false);
    HComponent labelComponent = new HComponent("PresentationName", label);
    HLayout labelLayout = new HLayout();
    labelLayout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT));
    labelLayout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.TOP));
    labelComponent.setLayout(labelLayout);
    return labelComponent;
  }

  protected static HComponent createPageNumberLabelComponent() {
    HLabelComponent label = new HLabelComponent();
    label.setLabel("Page #${PAGE_NUMBER}");
    label.setBorder(false);
    HComponent labelComponent = new HComponent("FooterLabel", label);
    labelComponent.setLayout(new HLayoutBuilder().left().bottom().build());
    return labelComponent;
  }

  protected static HComponent createSysdateLabelComponent() {
    HLabelComponent label = new HLabelComponent();
    label.setLabel("${SYSTEM_DATE}");
    label.setBorder(false);
    HComponent labelComponent = new HComponent("SystemDate", label);
    labelComponent.setLayout(new HLayoutBuilder().right().bottom().build());
    return labelComponent;
  }

  public HPresentation[] getAvailablePresentations() throws Exception {
    int nr = 1;
    return new HPresentation[] {
      new BarChartPresentationUtil(metadataProvider, variables)
          .createBarChartPresentation(100 * nr),
      new BarChartPresentationUtil(metadataProvider, variables)
          .createStackedBarChartPresentation(100 * nr),
      new LabelPresentationUtil(metadataProvider, variables).createLabelPresentation(100 * nr++),
      new LineChartPresentationUtil(metadataProvider, variables)
          .createLineChartPresentation(100 * nr++),
      new LineChartPresentationUtil(metadataProvider, variables)
          .createLineChartSeriesPresentation(100 * nr++),
      new LineChartPresentationUtil(metadataProvider, variables)
          .createLineChartNoLabelsPresentation(100 * nr++),
      new ComboPresentationUtil(metadataProvider, variables).createComboPresentation(100 * nr++),
      new CompositePresentationUtil(metadataProvider, variables)
          .createSimpleCompositePresentation(100 * nr++),
      new CrosstabPresentationUtil(metadataProvider, variables)
          .createCrosstabPresentation(100 * nr++),
      new CrosstabPresentationUtil(metadataProvider, variables)
          .createCrosstabPresentationOnlyVerticalDimensions(100 * nr++),
      new CrosstabPresentationUtil(metadataProvider, variables)
          .createCrosstabPresentationOnlyHorizontalDimensions(100 * nr++),
      new CrosstabPresentationUtil(metadataProvider, variables)
          .createCrosstabPresentationOnlyFacts(100 * nr++),
      new GroupCompositePresentationUtil(metadataProvider, variables)
          .createGroupCompositePresentation(100 * nr++),
      new GroupPresentationUtil(metadataProvider, variables)
          .createSimpleGroupedLabelPresentation(100 * nr++),
    };
  }

  public IHopMetadataProvider getMetadataProvider() {
    return metadataProvider;
  }

  public void setMetadataProvider(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }
}
