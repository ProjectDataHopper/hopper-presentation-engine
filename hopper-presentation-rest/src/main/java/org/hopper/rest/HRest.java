package org.hopper.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.encryption.HopTwoWayPasswordEncoder;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.HAttachment;
import org.hopper.core.HColorRGB;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.HDimension;
import org.hopper.core.HEnvironment;
import org.hopper.core.HFact;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sampledata.HSampleDataConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;
import org.hopper.presentation.variable.HParameter;
import org.hopper.presentation.variable.HParameterMapping;
import org.hopper.render.IRenderContext;
import org.hopper.render.context.PresentationRenderContext;
import org.hopper.rest.render.IRendering;

public class HRest {
  public static final String CONNECTOR_STEEL_WHEELS_NAME = "SteelWheels";

  private static HRest hopperRest;
  private final LoggingObject loggingObject;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final LogChannel log;
  private final String metadataPath;
  private final boolean corsAllowOrigin;

  private IHopMetadataSerializer<HPresentation> presentationSerializer;
  private IHopMetadataSerializer<HDatabaseConnection> dbConnSerializer;
  private IHopMetadataSerializer<HTheme> themeSerializer;
  private IHopMetadataSerializer<IHopMetadata> metadataSerializer;

  private final Map<String, IRendering> renderCache;

  public HRest() {
    loggingObject = new LoggingObject("Hopper Presentation REST");
    log = new LogChannel("Hopper Presentation REST Server");
    renderCache = new ConcurrentHashMap<>();
    System.out.println("Initializing the Hopper Presentation environment.");
    try {
      HEnvironment.init();
    } catch (Exception e) {
      log.logError("Could not initialize the Hopper environment", e);
    }
    log.logBasic("Starting the Hopper Presentation REST services application.");
    Properties props = new Properties();
    String propertyPath;
    try {
      String configPath = System.getProperty("HOPPER_REST_CONFIG_PATH");
      if (StringUtils.isEmpty(configPath)) {
        propertyPath = "/config";
      } else {
        propertyPath = configPath;
      }
      String configFileName = propertyPath + "/hopper-presentation.properties";
      log.logBasic("Finding configuration file: " + configFileName);

      File configFile = new File(configFileName);
      if (configFile.exists()) {
        log.logBasic("Found configuration file: " + configFileName);
        try (InputStream inputStream = new FileInputStream(configFile)) {
          props.load(inputStream);
          log.logBasic("Loaded configuration file: " + configFileName);
        }
      } else {
        log.logBasic("Unable to find config file " + configFileName);
        try (InputStream inputStream = getClass().getResourceAsStream("/hopper-presentation.properties")) {
          if (inputStream == null) {
            throw new IOException("Unable to find configuration hopper-presentation.properties");
          }
          props.load(inputStream);
        }
      }
    } catch (IOException e) {
      log.logError("Error initializing Hopper Presentation REST: ", e);
    }
    log.logBasic("Loading settings from configuration file.");

    metadataPath = props.getProperty("metadata.path");
    variables = Variables.getADefaultVariableSpace();
    metadataProvider =
        new JsonMetadataProvider(new HopTwoWayPasswordEncoder(), metadataPath, variables);
    corsAllowOrigin = Const.toBoolean(props.getProperty("cors.allow.origin"));

    log.logBasic("Found " + metadataProvider.getMetadataClasses().size() + " metadata types.");

    try {
      presentationSerializer = metadataProvider.getSerializer(HPresentation.class);
      dbConnSerializer = metadataProvider.getSerializer(HDatabaseConnection.class);
      themeSerializer = metadataProvider.getSerializer(HTheme.class);

      // createConnections();
      // createExecutionDetailsPresentation();
      // importPresentations();
    } catch (Exception e) {
      log.logError("Error creating presentation serializer: ", e);
    }
  }

  private void createExecutionDetailsPresentation() throws Exception {

    IHopMetadataSerializer<HConnector> serializer =
        metadataProvider.getSerializer(HConnector.class);

    // Create a presentation to list the available presentations
    //
    HPresentation presentation = new HPresentation();
    presentation.setName("execution-details");
    presentation.setDefaultThemeName(HTheme.getDefault().getName());

    // Map some parameters
    //
    HParameterMapping parameterMapping = new HParameterMapping();
    parameterMapping.setConnectorName("hop-execution-details");
    parameterMapping.setMappings(
        Arrays.asList(
            new HParameterMapping.FieldToParameterMapping("executionId", "EXECUTION_ID"),
            new HParameterMapping.FieldToParameterMapping("name", "EXECUTION_NAME"),
            new HParameterMapping.FieldToParameterMapping("executionType", "EXECUTION_TYPE"),
            new HParameterMapping.FieldToParameterMapping("filename", "EXECUTION_FILENAME"),
            new HParameterMapping.FieldToParameterMapping("loggingText", "EXECUTION_LOGGING")));

    presentation.getParameterMappings().add(parameterMapping);

    // Now these variables are available in all components
    //

    // Add one page
    HPage page = HPage.getA4(false);
    presentation.getPages().add(page);

    // Italic font
    //
    HFont italicFont = new HFont(HTheme.getDefault().getDefaultFont());
    italicFont.setItalic(true);

    // Add a few label components
    //
    int verticalMargin = 5;
    int horizontalMargin = 15;
    HLabelComponent idLabelComponent = new HLabelComponent("Execution ID: ");
    HComponent idLabel = new HComponent("label-execution-id", idLabelComponent);
    idLabel.setLayout(HLayout.topLeftPage());
    page.getComponents().add(idLabel);

    // ID
    //
    HLabelComponent idValueComponent = new HLabelComponent("${EXECUTION_ID}");
    HComponent idValue = new HComponent("value-execution-id", idValueComponent);
    idValue.setLayout(new HLayoutBuilder().beside(idLabel.getName(), horizontalMargin).build());
    page.getComponents().add(idValue);

    HLabelComponent nameLabelComponent = new HLabelComponent("Name: ");
    HComponent nameLabel = new HComponent("label-execution-name", nameLabelComponent);
    nameLabel.setLayout(
        new HLayoutBuilder().below(idLabel.getName(), verticalMargin).left().build());
    page.getComponents().add(nameLabel);

    // Name
    //
    HLabelComponent nameValueComponent = new HLabelComponent("${EXECUTION_NAME}");
    HComponent nameValue = new HComponent("value-execution-name", nameValueComponent);
    nameValue.setLayout(
        new HLayoutBuilder()
            .left(
                new HAttachment(
                    idLabel.getName(), 0, horizontalMargin, HAttachment.Alignment.RIGHT))
            .top(
                new HAttachment(
                    idLabel.getName(), 0, verticalMargin, HAttachment.Alignment.BOTTOM))
            .build());
    page.getComponents().add(nameValue);

    // Filename
    //
    HLabelComponent filenameLabelComponent = new HLabelComponent("Filename: ");
    HComponent filenameLabel =
        new HComponent("label-execution-filename", filenameLabelComponent);
    filenameLabel.setLayout(
        new HLayoutBuilder().below(nameLabel.getName(), verticalMargin).left().build());
    page.getComponents().add(filenameLabel);

    HLabelComponent filenameValueComponent = new HLabelComponent("${EXECUTION_FILENAME}");
    HComponent filenameValue =
        new HComponent("value-execution-filename", filenameValueComponent);
    filenameValue.setLayout(
        new HLayoutBuilder()
            .left(
                new HAttachment(
                    idLabel.getName(), 0, horizontalMargin, HAttachment.Alignment.RIGHT))
            .top(
                new HAttachment(
                    nameLabel.getName(), 0, verticalMargin, HAttachment.Alignment.BOTTOM))
            .build());
    page.getComponents().add(filenameValue);

    // Type
    //
    HLabelComponent typeLabelComponent = new HLabelComponent("Type: ");
    HComponent typeLabel = new HComponent("label-execution-type", typeLabelComponent);
    typeLabel.setLayout(
        new HLayoutBuilder().below(filenameLabel.getName(), verticalMargin).left().build());
    page.getComponents().add(typeLabel);

    HLabelComponent typeValueComponent = new HLabelComponent("${EXECUTION_TYPE}");
    HComponent typeValue = new HComponent("value-execution-type", typeValueComponent);
    typeValue.setLayout(
        new HLayoutBuilder()
            .left(
                new HAttachment(
                    idLabel.getName(), 0, horizontalMargin, HAttachment.Alignment.RIGHT))
            .top(
                new HAttachment(
                    filenameLabel.getName(), 0, verticalMargin, HAttachment.Alignment.BOTTOM))
            .build());
    page.getComponents().add(typeValue);

    // Execution duration trend
    //
    HLineChartComponent lineChartComponent =
        new HLineChartComponent("hop-list-execution-durations");
    lineChartComponent
        .getHorizontalDimensions()
        .add(
            new HDimension(
                "nr", "Execution", HHorizontalAlignment.CENTER, HVerticalAlignment.MIDDLE));
    lineChartComponent
        .getFacts()
        .add(
            new HFact(
                "duration",
                "ms",
                HHorizontalAlignment.CENTER,
                HVerticalAlignment.MIDDLE,
                AggregationMethod.SUM,
                "###,##0"));
    lineChartComponent.setDrawingCurvedTrendLine(true);
    lineChartComponent.setDotSize(3);
    lineChartComponent.setShowingLegend(true);
    lineChartComponent.setShowingHorizontalLabels(false);
    lineChartComponent.setShowingVerticalLabels(true);
    lineChartComponent.setLineWidth("1");
    lineChartComponent.setTitle("Execution duration trend (ms) for ${EXECUTION_NAME}");
    HComponent lineChart = new HComponent("execution-duration-trend", lineChartComponent);
    lineChart.setLayout(
        new HLayoutBuilder()
            .below(typeLabel.getName(), 2 * verticalMargin)
            .right(new HAttachment(null, 0, 600, HAttachment.Alignment.LEFT))
            .bottom(
                new HAttachment(typeLabel.getName(), 0, 400, HAttachment.Alignment.BOTTOM))
            .build());
    page.getComponents().add(lineChart);

    // Save the presentation
    presentationSerializer.save(presentation);
  }

  private void importPresentations() throws Exception {
    for (String filename :
        List.of("import/combo-test.json", "import/grouped-composite-test.json")) {
      try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
        String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        HPresentation presentation = HPresentation.fromJsonString(json);
        if (presentationSerializer.exists(presentation.getName())) {
          presentationSerializer.delete(presentation.getName());
        }
        presentationSerializer.save(presentation);
      }
    }
  }

  public static HRest getInstance() {
    if (hopperRest == null) {
      hopperRest = new HRest();
    }
    return hopperRest;
  }

  public HPresentation loadPresentation(String presentationName)
      throws HException, HopException {
    HPresentation presentation = presentationSerializer.load(presentationName);
    if (presentation == null) {
      throw new HException("Unable to find presentation '" + presentationName + "'");
    }
    return presentation;
  }

  private List<HRenderPage> renderPresentation(String presentationName)
      throws HException, HopException {
    HPresentation presentation = loadPresentation(presentationName);
    IRenderContext renderContext = new PresentationRenderContext(presentation, metadataProvider);
    HLayoutResults layout =
        presentation.doLayout(
            loggingObject, renderContext, metadataProvider, Collections.emptyList());
    presentation.render(layout, metadataProvider);
    return layout.getRenderPages();
  }

  public String getPresentationSVG(String presentationName, int pageNumber)
      throws HopException, HException {
    log.logBasic("Loading presentation " + presentationName);

    List<HRenderPage> renderPages = renderPresentation(presentationName);
    return renderPages.get(pageNumber).getSvgXml();
  }

  public List<String> getComponentsAt(String presentationName, int pageNb, String posXY)
      throws HopException, HException {
    int posX = Integer.parseInt(posXY.split(",")[0]);
    int posY = Integer.parseInt(posXY.split(",")[1]);

    List<HRenderPage> renderPages = renderPresentation(presentationName);
    HRenderPage renderPage = renderPages.get(pageNb);

    return renderPage.lookupComponentName(posX, posY);
  }

  private void createConnections() throws HopException {
    HDatabaseConnection connection2 =
        new HDatabaseConnection(
            "logging", "POSTGRESQL", "localhost", "5432", "logging", "postgres", "postgres");
    String h2DatabaseName =
        System.getProperty("java.io.tmpdir") + File.separator + CONNECTOR_STEEL_WHEELS_NAME;

    HDatabaseConnection swConnection =
        new HDatabaseConnection(
            CONNECTOR_STEEL_WHEELS_NAME, "H2", null, null, h2DatabaseName, null, null);

    try {
      dbConnSerializer.save(connection2);
      dbConnSerializer.save(swConnection);
    } catch (HopException e) {
      log.logError("Error saving dummy connections: " + e.getMessage());
    }

    try {
      // Delete old database
      //
      File[] files =
          new File(System.getProperty("java.io.tmpdir"))
              .listFiles(
                  pathname ->
                      pathname.toString().endsWith(".db")
                          && pathname.toString().contains(CONNECTOR_STEEL_WHEELS_NAME));
      for (File file : files) {
        FileUtils.forceDelete(file);
      }

      // Read the script
      //
      List<String> lines =
          Files.readAllLines(
              Paths.get(getClass().getClassLoader().getResource("steelwheels.script").getPath()));

      DatabaseMeta databaseMeta = swConnection.createDatabaseMeta();
      databaseMeta.setForcingIdentifiersToUpperCase(true);
      try (Database database =
          new Database(new LoggingObject(swConnection.getName()), variables, databaseMeta)) {
        database.connect();
        for (String line : lines) {
          database.execStatement(line);
        }
      }

      // Also create some Hopper connectors
      //
      HSqlConnector territoriesConnector =
          new HSqlConnector(
              "SteelWheels",
              "select coalesce(territory, 'UNKNOWN') as territory, count(*) as cnt "
                  + "from customer_w_ter "
                  + "group by territory "
                  + "order by 1 asc; ");
      HConnector territories = new HConnector("territories", territoriesConnector);
      metadataProvider.getSerializer(HConnector.class).save(territories);

      HSampleDataConnector sampleDataConnector = new HSampleDataConnector(100);
      HConnector sampleData = new HConnector("Sample Data", sampleDataConnector);
      metadataProvider.getSerializer(HConnector.class).save(sampleData);

    } catch (Exception e) {
      throw new HopException("Error saving connections", e);
    }
    log.logDetailed("Steel Wheels database created");
  }

  private void createTestThemes() throws HopException {

    HFont theme1Font = new HFont("Arial", "14", false, false);
    HFont title1Font = new HFont("Arial", "20", true, true);
    HColorRGB t1C1 = new HColorRGB(255, 200, 200);
    HColorRGB t1C2 = new HColorRGB(250, 210, 210);
    HColorRGB t1C3 = new HColorRGB(180, 180, 180);
    HColorRGB t1C4 = new HColorRGB(200, 100, 100);

    HTheme theme1 = new HTheme();
    theme1.setName("First Theme");
    theme1.setDefaultFont(theme1Font);
    theme1.setDefaultColor(t1C1);
    theme1.setBorderColor(t1C2);
    theme1.setTitleFont(title1Font);
    theme1.setBorderColor(t1C3);
    theme1.setTitleColor(t1C4);
    theme1.setColors(Arrays.asList(t1C1, t1C2, t1C3, t1C4));

    HFont theme2Font = new HFont("Arial", "14", false, false);
    HFont title2Font = new HFont("Arial", "20", true, true);
    HColorRGB t2C1 = new HColorRGB(200, 200, 244);
    HColorRGB t2C2 = new HColorRGB(200, 210, 255);
    HColorRGB t2C3 = new HColorRGB(180, 180, 250);
    HColorRGB t2C4 = new HColorRGB(100, 100, 250);

    HTheme theme2 = new HTheme();
    theme2.setName("Second Theme");
    theme2.setDefaultFont(theme2Font);
    theme2.setDefaultColor(t2C1);
    theme2.setBorderColor(t2C2);
    theme2.setTitleFont(title2Font);
    theme2.setBorderColor(t2C3);
    theme2.setTitleColor(t2C4);
    theme2.setColors(Arrays.asList(t2C1, t2C2, t2C3, t2C4));

    try {
      themeSerializer.save(theme1);
      themeSerializer.save(theme2);
    } catch (HopException e) {
      throw new HopException("Error saving test themes", e);
    }
  }

  public void storeRendering(IRendering rendering) {
    renderCache.put(rendering.getId(), rendering);
  }

  public void removeRendering(IRendering rendering) {
    renderCache.remove(rendering.getId());
  }

  public IRendering getRendering(String id) {
    return renderCache.get(id);
  }

  public IRendering findRendering(String presentationName, List<HParameter> parameters) {
    for (IRendering rendering : renderCache.values()) {
      if (presentationName.equals(rendering.getPresentationName())) {
        // Verify that the parameters are all the same with the same values.
        //
        if (rendering.getParameters().size() != parameters.size()) {
          return null; // different rendering
        }
        for (int i = 0; i < parameters.size(); i++) {
          if (!parameters.get(i).equals(rendering.getParameters().get(i))) {
            return null;
          }
        }
        return rendering;
      }
    }
    return null;
  }

  /**
   * Sets hopperRest
   *
   * @param hopperRest value of hopperRest
   */
  public static void setHopperUtil(HRest hopperRest) {
    HRest.hopperRest = hopperRest;
  }

  /**
   * Gets loggingObject
   *
   * @return value of loggingObject
   */
  public LoggingObject getLoggingObject() {
    return loggingObject;
  }

  /**
   * Gets variables
   *
   * @return value of variables
   */
  public IVariables getVariables() {
    return variables;
  }

  /**
   * Gets metadataProvider
   *
   * @return value of metadataProvider
   */
  public IHopMetadataProvider getMetadataProvider() {
    return metadataProvider;
  }

  /**
   * Gets log
   *
   * @return value of log
   */
  public LogChannel getLog() {
    return log;
  }

  /**
   * Gets metadataPath
   *
   * @return value of metadataPath
   */
  public String getMetadataPath() {
    return metadataPath;
  }

  /**
   * Gets presentationSerializer
   *
   * @return value of presentationSerializer
   */
  public IHopMetadataSerializer<HPresentation> getPresentationSerializer() {
    return presentationSerializer;
  }

  /**
   * Sets presentationSerializer
   *
   * @param presentationSerializer value of presentationSerializer
   */
  public void setPresentationSerializer(
      IHopMetadataSerializer<HPresentation> presentationSerializer) {
    this.presentationSerializer = presentationSerializer;
  }

  /**
   * Gets dbConnSerializer
   *
   * @return value of dbConnSerializer
   */
  public IHopMetadataSerializer<HDatabaseConnection> getDbConnSerializer() {
    return dbConnSerializer;
  }

  /**
   * Sets dbConnSerializer
   *
   * @param dbConnSerializer value of dbConnSerializer
   */
  public void setDbConnSerializer(IHopMetadataSerializer<HDatabaseConnection> dbConnSerializer) {
    this.dbConnSerializer = dbConnSerializer;
  }

  /**
   * Gets themeSerializer
   *
   * @return value of themeSerializer
   */
  public IHopMetadataSerializer<HTheme> getThemeSerializer() {
    return themeSerializer;
  }

  /**
   * Sets themeSerializer
   *
   * @param themeSerializer value of themeSerializer
   */
  public void setThemeSerializer(IHopMetadataSerializer<HTheme> themeSerializer) {
    this.themeSerializer = themeSerializer;
  }

  /**
   * Gets metadataSerializer
   *
   * @return value of metadataSerializer
   */
  public IHopMetadataSerializer<IHopMetadata> getMetadataSerializer() {
    return metadataSerializer;
  }

  /**
   * Sets metadataSerializer
   *
   * @param metadataSerializer value of metadataSerializer
   */
  public void setMetadataSerializer(IHopMetadataSerializer<IHopMetadata> metadataSerializer) {
    this.metadataSerializer = metadataSerializer;
  }

  /**
   * Gets corsAllowOrigin
   *
   * @return value of corsAllowOrigin
   */
  public boolean isCorsAllowOrigin() {
    return corsAllowOrigin;
  }
}
