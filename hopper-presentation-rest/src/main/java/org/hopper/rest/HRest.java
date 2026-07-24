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
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.apache.hop.metadata.util.HopMetadataInstance;
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
import org.hopper.audit.HAuditConfig;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditSinkLoader;
import org.hopper.presentation.datacontext.HConnectorCacheSettings;
import org.hopper.presentation.datacontext.HGlobalVariables;
import org.hopper.presentation.layout.HLayoutCacheSettings;
import org.hopper.presentation.layout.HPresentationLayoutCache;
import org.hopper.rest.admin.AdminSettingsService;
import org.hopper.rest.admin.AdminVariablesService;
import org.hopper.rest.admin.HServerHousekeeping;
import org.hopper.rest.admin.oauth.OAuthAdminService;
import org.hopper.rest.render.IRendering;
import org.hopper.rest.render.RenderCache;
import org.hopper.rest.security.HAuthMode;
import org.hopper.rest.security.HSecuritySettings;
import org.hopper.rest.security.HSessionStore;
import org.hopper.rest.security.OAuth2JwtValidator;
import org.hopper.rest.security.OidcBrowserLoginService;
import org.hopper.security.DefaultHAuthorizationService;
import org.hopper.security.DefaultHRoleGrantResolver;
import org.hopper.security.HPrincipal;
import org.hopper.security.HPrincipalEnricher;
import org.hopper.security.HSecurityContext;
import org.hopper.security.MetadataHAclProvider;
import org.hopper.security.MetadataHRoleSource;
import org.hopper.security.MetadataHUserAssignmentSource;

public class HRest {
  public static final String CONNECTOR_STEEL_WHEELS_NAME = "SteelWheels";

  private static HRest hopperRest;
  private final LoggingObject loggingObject;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final LogChannel log;
  private final String metadataPath;
  private volatile boolean corsAllowOrigin;
  private volatile HSecuritySettings securitySettings;
  private volatile OAuth2JwtValidator oauth2JwtValidator;
  private volatile OidcBrowserLoginService oidcBrowserLoginService;
  private final AdminSettingsService adminSettingsService;
  private final AdminVariablesService adminVariablesService;
  private final OAuthAdminService oauthAdminService;
  private final DefaultHRoleGrantResolver roleGrantResolver;
  private final HPrincipalEnricher principalEnricher;

  private IHopMetadataSerializer<HPresentation> presentationSerializer;
  private IHopMetadataSerializer<HDatabaseConnection> dbConnSerializer;
  private IHopMetadataSerializer<HTheme> themeSerializer;
  private IHopMetadataSerializer<IHopMetadata> metadataSerializer;

  public HRest() {
    loggingObject = new LoggingObject("Hopper Presentation REST");
    log = new LogChannel("Hopper Presentation REST Server");
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
      // Prefer environment variable (Docker -e) over system property (-D...).
      // Older images set CATALINA_OPTS=-DHOPPER_REST_CONFIG_PATH=/config which would
      // otherwise ignore a mounted config path passed only via env.
      String configPath = System.getenv("HOPPER_REST_CONFIG_PATH");
      if (StringUtils.isEmpty(configPath)) {
        configPath = System.getProperty("HOPPER_REST_CONFIG_PATH");
      }
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

    String rawMetadataPath = props.getProperty("metadata.path");
    if (StringUtils.isEmpty(rawMetadataPath)) {
      log.logError(
          "metadata.path is not set in hopper-presentation.properties — "
              + "check HOPPER_REST_CONFIG_PATH (system property or environment variable)");
      rawMetadataPath = "metadata";
    }
    // Resolve relative metadata.path against the config directory when possible
    File metadataDir = new File(rawMetadataPath);
    if (!metadataDir.isAbsolute()) {
      String configDirHint = System.getenv("HOPPER_REST_CONFIG_PATH");
      if (StringUtils.isEmpty(configDirHint)) {
        configDirHint = System.getProperty("HOPPER_REST_CONFIG_PATH");
      }
      if (StringUtils.isNotEmpty(configDirHint)) {
        metadataDir = new File(configDirHint, rawMetadataPath);
      }
    }
    metadataPath = metadataDir.getAbsolutePath();
    log.logBasic("Using metadata.path=" + metadataPath);
    if (!metadataDir.isDirectory()) {
      log.logError("metadata.path does not exist or is not a directory: " + metadataPath);
    }
    // Available to connectors as ${HOPPER_METADATA_PATH} / env for ops sample CSV paths
    System.setProperty("HOPPER_METADATA_PATH", metadataPath);
    // Expose resolved path in bootstrap props for admin effective-settings view
    props.setProperty("metadata.path", metadataPath);

    variables = Variables.getADefaultVariableSpace();
    metadataProvider =
        new JsonMetadataProvider(new HopTwoWayPasswordEncoder(), metadataPath, variables);
    // So #{variable-resolver:…} expressions resolve against this metadata (Hop Variables)
    HopMetadataInstance.setMetadataProvider(
        new MultiMetadataProvider(variables, metadataProvider));
    HGlobalVariables.set(variables);

    // Layered settings: bootstrap (L0) + runtime overrides (L1) from metadata
    adminSettingsService = new AdminSettingsService(props, metadataProvider);
    try {
      adminSettingsService.loadFromMetadata();
      if (!adminSettingsService.getOverrides().isEmpty()) {
        log.logBasic(
            "Loaded "
                + adminSettingsService.getOverrides().size()
                + " runtime setting override(s) from server-settings/runtime");
      }
    } catch (Exception e) {
      log.logError("Could not load runtime setting overrides", e);
    }

    // System variables (admin panel) → shared IVariables inherited by presentations
    adminVariablesService = new AdminVariablesService(metadataProvider);
    try {
      adminVariablesService.loadFromMetadata();
      adminVariablesService.applyTo(variables);
      if (!adminVariablesService.getVariables().isEmpty()) {
        log.logBasic(
            "Loaded "
                + adminVariablesService.getVariables().size()
                + " system variable(s) from system-variables/runtime");
      }
    } catch (Exception e) {
      log.logError("Could not load system variables", e);
    }

    Properties effectiveProps = adminSettingsService.effectiveProperties();
    corsAllowOrigin = Const.toBoolean(effectiveProps.getProperty("cors.allow.origin"));
    securitySettings = new HSecuritySettings(effectiveProps);
    rebuildAuthServices(securitySettings);

    HSessionStore.getInstance()
        .setTtl(java.time.Duration.ofMinutes(Math.max(5, securitySettings.getSessionTtlMinutes())));

    // Custom roles + user assignments from metadata
    roleGrantResolver = new DefaultHRoleGrantResolver(new MetadataHRoleSource(metadataProvider));
    principalEnricher =
        new HPrincipalEnricher(new MetadataHUserAssignmentSource(metadataProvider));
    oauthAdminService = new OAuthAdminService(this);

    // Authorization service with optional resource ACLs + custom role grants
    installAuthorizationService(securitySettings);
    log.logBasic(
        "Authorization service ready (defaultDenyResources="
            + securitySettings.isDefaultDenyResources()
            + ", custom roles enabled)");

    try {
      HAuditConfig auditConfig = HAuditConfig.fromProperties(effectiveProps);
      HAuditSinkLoader.bootstrap(
          HAuditEmitter.getInstance(), auditConfig, metadataProvider, variables);
      log.logBasic(
          "Audit configured: enabled="
              + auditConfig.isEnabled()
              + " async="
              + auditConfig.isAsync()
              + " sinks="
              + HAuditEmitter.getInstance().getSinks().size());
    } catch (Exception e) {
      log.logError("Could not bootstrap audit sinks", e);
    }

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

    applyServerOpsSettings(effectiveProps);
  }

  /** Configure render cache TTL/max and start housekeeping sweeper from effective properties. */
  public void applyServerOpsSettings(Properties props) {
    Properties p = props != null ? props : new Properties();
    int ttl = parsePositiveInt(p.getProperty("server.render.ttl-minutes"), 60);
    int max = parsePositiveInt(p.getProperty("server.render.max-entries"), 200);
    int sweep = parsePositiveInt(p.getProperty("server.session.sweep-interval-seconds"), 60);
    RenderCache.getInstance().configure(ttl, max);
    HServerHousekeeping hk = HServerHousekeeping.getInstance();
    hk.applyRenderSettings(ttl, max);
    hk.start(sweep);
    // Per-layout connector result cache (shared query when many components use the same connector)
    HConnectorCacheSettings.applyFromProperties(p);
    HLayoutCacheSettings.applyFromProperties(p);
    log.logBasic(
        "Server ops: render ttlMinutes="
            + ttl
            + " maxEntries="
            + max
            + " sweepIntervalSeconds="
            + sweep
            + " connectorCacheEnabled="
            + HConnectorCacheSettings.isEnabled()
            + " connectorCacheMaxRows="
            + HConnectorCacheSettings.getMaxRows()
            + " layoutCacheEnabled="
            + HLayoutCacheSettings.isEnabled()
            + " layoutCacheMaxComponents="
            + HLayoutCacheSettings.getMaxComponents());
  }

  private static int parsePositiveInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      int n = Integer.parseInt(value.trim());
      return n > 0 ? n : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
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
    if (rendering == null) {
      return;
    }
    RenderCache.getInstance().put(rendering);
    org.hopper.rest.security.HActiveUsageRegistry.getInstance()
        .start(
            rendering.getId(),
            rendering.getPresentationName(),
            org.hopper.security.HSecurityContext.getPrincipal(),
            org.hopper.security.HSecurityContext.getRequestId());
  }

  public void removeRendering(IRendering rendering) {
    if (rendering == null) {
      return;
    }
    RenderCache.getInstance().remove(rendering);
    org.hopper.rest.security.HActiveUsageRegistry.getInstance().end(rendering.getId());
  }

  public IRendering removeRenderingById(String id) {
    IRendering removed = RenderCache.getInstance().remove(id);
    if (removed != null) {
      org.hopper.rest.security.HActiveUsageRegistry.getInstance().end(id);
    }
    return removed;
  }

  public void clearRenderings() {
    for (IRendering r : RenderCache.getInstance().values()) {
      if (r != null) {
        org.hopper.rest.security.HActiveUsageRegistry.getInstance().end(r.getId());
      }
    }
    RenderCache.getInstance().clear();
  }

  public IRendering getRendering(String id) {
    return RenderCache.getInstance().get(id);
  }

  public IRendering findRendering(String presentationName, List<HParameter> parameters) {
    for (IRendering rendering : RenderCache.getInstance().values()) {
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
        // Touch cache entry via get
        return getRendering(rendering.getId());
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

  /**
   * Authentication / authorization settings from configuration.
   *
   * @return security settings (never null)
   */
  public HSecuritySettings getSecuritySettings() {
    return securitySettings;
  }

  /**
   * OAuth2 JWT validator when {@code auth.mode=oauth2}; otherwise {@code null}.
   *
   * @return validator or null
   */
  public OAuth2JwtValidator getOAuth2JwtValidator() {
    return oauth2JwtValidator;
  }

  public OidcBrowserLoginService getOidcBrowserLoginService() {
    return oidcBrowserLoginService;
  }

  public AdminSettingsService getAdminSettingsService() {
    return adminSettingsService;
  }

  public AdminVariablesService getAdminVariablesService() {
    return adminVariablesService;
  }

  public OAuthAdminService getOAuthAdminService() {
    return oauthAdminService;
  }

  /**
   * Persist a settings patch (L1 overrides) and hot-apply security / session / CORS where possible.
   */
  public synchronized AdminSettingsService.ApplyResult applySettingsPatch(
      Map<String, String> patch) throws Exception {
    AdminSettingsService.ApplyResult result = adminSettingsService.applyPatch(patch);
    if (!result.success()) {
      return result;
    }
    Properties effectiveProps = adminSettingsService.effectiveProperties();
    corsAllowOrigin = Const.toBoolean(effectiveProps.getProperty("cors.allow.origin"));
    HSecuritySettings next = new HSecuritySettings(effectiveProps);
    rebuildAuthServices(next);
    HSessionStore.getInstance()
        .setTtl(java.time.Duration.ofMinutes(Math.max(5, next.getSessionTtlMinutes())));
    installAuthorizationService(next);
    applyServerOpsSettings(effectiveProps);
    log.logBasic(
        "Applied runtime settings patch: keys="
            + result.applied()
            + (result.restartRequired().isEmpty()
                ? ""
                : (" restartRequired=" + result.restartRequired())));
    return result;
  }

  public DefaultHRoleGrantResolver getRoleGrantResolver() {
    return roleGrantResolver;
  }

  public HPrincipalEnricher getPrincipalEnricher() {
    return principalEnricher;
  }

  /** Merge Hopper user assignments into the authenticated principal (additive roles). */
  public HPrincipal enrichPrincipal(HPrincipal principal) {
    if (principalEnricher == null) {
      return principal;
    }
    return principalEnricher.enrich(principal);
  }

  /** Call after custom role CRUD so grant cache refreshes. */
  public void invalidateRoleGrants() {
    if (roleGrantResolver != null) {
      roleGrantResolver.invalidate();
    }
  }

  private void installAuthorizationService(HSecuritySettings settings) {
    boolean defaultDeny =
        settings != null && settings.isDefaultDenyResources();
    HSecurityContext.setAuthorizationService(
        new DefaultHAuthorizationService(
            new MetadataHAclProvider(metadataProvider), defaultDeny, roleGrantResolver));
  }

  private void rebuildAuthServices(HSecuritySettings settings) {
    this.securitySettings = settings != null ? settings : HSecuritySettings.disabled();
    if (this.securitySettings.isAuthEnabled()) {
      log.logBasic(
          "Authentication enabled: mode="
              + this.securitySettings.getAuthMode()
              + (this.securitySettings.getAuthMode() == HAuthMode.STATIC_DEV
                  ? (" user="
                      + this.securitySettings.getDevUser()
                      + " roles="
                      + this.securitySettings.getDevRoles())
                  : this.securitySettings.getAuthMode() == HAuthMode.OAUTH2
                      ? (" issuer="
                          + this.securitySettings.getIssuerUri()
                          + " audience="
                          + this.securitySettings.getAudience())
                      : ""));
    } else {
      log.logBasic(
          "Authentication is DISABLED — REST API is open. Set auth.enabled=true and auth.mode for production.");
    }

    if (this.securitySettings.isAuthEnabled()
        && this.securitySettings.getAuthMode() == HAuthMode.OAUTH2) {
      oauth2JwtValidator = new OAuth2JwtValidator(this.securitySettings);
      log.logBasic("OAuth2 JWT resource server validator registered");
      oidcBrowserLoginService =
          new OidcBrowserLoginService(this.securitySettings, oauth2JwtValidator);
      if (oidcBrowserLoginService.isConfigured()) {
        log.logBasic(
            "OIDC browser login (PKCE) configured clientId="
                + this.securitySettings.getOidcClientId());
      } else {
        log.logBasic(
            "OIDC browser login not fully configured (set auth.oidc.client-id + auth.issuer-uri)");
      }
    } else {
      oauth2JwtValidator = null;
      oidcBrowserLoginService = null;
    }
  }
}
