package org.hopper.rest.resources;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.DatabasePluginType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.lineage.HUsageAudit;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.db.HDatabaseConnectionPool;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.page.HPage;
import org.hopper.rest.HRest;
import org.hopper.rest.resources.requests.ModifyComponentRequest;
import org.hopper.rest.resources.requests.ModifyConnectorRequest;
import org.hopper.rest.resources.responses.PresentationResponse;
import org.hopper.security.HSecurityContext;

@Path("/metadata")
public class MetadataResource extends BaseResource {

  private final HRest hopperRest = HRest.getInstance();

  /**
   * List all the type keys
   *
   * @return A list with all the type keys in the metadata
   */
  @GET
  @Path("/types")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getTypes(@Context SecurityContext securityContext) {
    try {
      List<String> types = new ArrayList<>();
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      List<Class<IHopMetadata>> metadataClasses = provider.getMetadataClasses();
      for (Class<IHopMetadata> metadataClass : metadataClasses) {
        HopMetadata metadata = metadataClass.getAnnotation(HopMetadata.class);
        types.add(metadata.key());
      }
      // Pre-serialize JSON (avoids Jersey FilteringJacksonJaxbJsonProvider NPE)
      String json = new ObjectMapper().writeValueAsString(types);
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError("Error listing metadata types", e);
    }
  }

  /**
   * List all the element names for a given type
   *
   * @param key the metadata key to use
   * @return A list with all the metadata element names
   * @throws HopException
   */
  @GET
  @Path("/list/{key}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listNames(@PathParam("key") String key) {
    try {
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      Class<IHopMetadata> metadataClass = provider.getMetadataClassForKey(key);
      IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(metadataClass);
      String json = new ObjectMapper().writeValueAsString(serializer.listObjectNames());
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError("Error listing metadata names for key " + key, e);
    }
  }

  /**
   * Get a metadata element with a given type and name as Hop metadata JSON (via {@link
   * JsonMetadataParser}, not raw Jackson entity binding).
   *
   * @param key The key of the metadata type
   * @param name The name to look up
   * @return The metadata element JSON
   */
  @GET
  @Path("/{key}/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getElement(@PathParam("key") String key, @PathParam("name") String name) {
    try {
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      Class<IHopMetadata> metadataClass = provider.getMetadataClassForKey(key);
      IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(metadataClass);
      IHopMetadata metadata = serializer.load(name);
      if (metadata == null) {
        return getServerError("Metadata not found: key=" + key + " name=" + name, false);
      }
      JsonMetadataParser<IHopMetadata> parser =
          new JsonMetadataParser<>(metadataClass, provider);
      org.json.simple.JSONObject json = parser.getJsonObject(metadata);
      return Response.ok()
          .entity(json.toJSONString())
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error loading metadata key=" + key + " name=" + name, e);
    }
  }

  /**
   * Delete a metadata element by type key and name.
   *
   * <p>Hop's {@link IHopMetadataSerializer#delete} loads the object first. Corrupt JSON (for
   * example a connector saved without the plugin-type wrapper) fails that load and blocks delete.
   * When the serializer cannot load the object we fall back to deleting the JSON file on disk so
   * the user can still remove broken metadata.
   *
   * @param key metadata type key
   * @param name element name
   */
  @DELETE
  @Path("/{key}/{name}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response deleteElement(@PathParam("key") String key, @PathParam("name") String name) {
    try {
      deleteMetadataElement(key, name);
      hopperRest.getLog().logBasic("Deleted metadata key='" + key + "' name='" + name + "'");
      HAuditEmitter.getInstance()
          .emitSafely(
              HUsageAudit.metadataChange(
                  HAuditEventType.METADATA_DELETE,
                  key,
                  name,
                  HSecurityContext.getPrincipal()));
      return Response.ok().entity(name).type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      return getServerError("Error deleting metadata key=" + key + " name=" + name, e);
    }
  }

  /**
   * Delete a metadata element, with file-level fallback when the object cannot be deserialized.
   *
   * @throws HException if the element does not exist or the file cannot be removed
   */
  void deleteMetadataElement(String key, String name) throws Exception {
    if (key == null || key.isBlank() || name == null || name.isBlank()) {
      throw new HException("Metadata key and name are required for delete");
    }
    IHopMetadataProvider provider = hopperRest.getMetadataProvider();
    Class<IHopMetadata> metadataClass = provider.getMetadataClassForKey(key);
    IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(metadataClass);

    boolean fileExists = metadataJsonFileExists(key, name);
    boolean serializerSaysExists;
    try {
      serializerSaysExists = serializer.exists(name);
    } catch (Exception e) {
      serializerSaysExists = fileExists;
    }
    if (!serializerSaysExists && !fileExists) {
      throw new HException("Metadata not found: key=" + key + " name=" + name);
    }

    try {
      serializer.delete(name);
      if ("hopper-database-connection".equals(key)) {
        HDatabaseConnectionPool.invalidate(name);
      }
      return;
    } catch (Exception loadOrDeleteFailed) {
      // Corrupt / unloadable JSON: remove the file so the UI can recover
      hopperRest
          .getLog()
          .logError(
              "Serializer delete failed for key='"
                  + key
                  + "' name='"
                  + name
                  + "'; removing JSON file directly",
              loadOrDeleteFailed);
      if (deleteMetadataJsonFile(key, name)) {
        if ("hopper-database-connection".equals(key)) {
          HDatabaseConnectionPool.invalidate(name);
        }
        return;
      }
      throw loadOrDeleteFailed;
    }
  }

  /** Absolute JSON path for a metadata element: {@code {metadata.path}/{key}/{name}.json}. */
  String metadataJsonFilename(String key, String name) {
    String base = hopperRest.getMetadataPath();
    if (base == null) {
      base = "";
    }
    if (!base.isEmpty() && !base.endsWith("/") && !base.endsWith("\\")) {
      base = base + "/";
    }
    return base + key + "/" + name + ".json";
  }

  boolean metadataJsonFileExists(String key, String name) {
    try {
      return HopVfs.fileExists(metadataJsonFilename(key, name));
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * @return true if the file was deleted or already absent
   */
  boolean deleteMetadataJsonFile(String key, String name) throws Exception {
    String filename = metadataJsonFilename(key, name);
    FileObject file = HopVfs.getFileObject(filename);
    if (!file.exists()) {
      return true;
    }
    boolean deleted = file.delete();
    if (!deleted) {
      throw new HException(
          "Could not delete metadata file for key=" + key + " name=" + name + " path=" + filename);
    }
    hopperRest
        .getLog()
        .logBasic("Deleted metadata JSON file for key='" + key + "' name='" + name + "'");
    return true;
  }

  /**
   * Hop database plugin type codes for the database-connection admin form: {@code [{ id, name },
   * ...]}.
   */
  @GET
  @Path("/database-types")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listDatabaseTypes() {
    try {
      PluginRegistry registry = PluginRegistry.getInstance();
      List<IPlugin> plugins = new ArrayList<>(registry.getPlugins(DatabasePluginType.class));
      plugins.sort(
          Comparator.comparing(
              p -> p.getName() != null ? p.getName() : p.getIds()[0],
              String.CASE_INSENSITIVE_ORDER));
      List<Map<String, String>> rows = new ArrayList<>();
      for (IPlugin plugin : plugins) {
        Map<String, String> row = new LinkedHashMap<>();
        String id = plugin.getIds() != null && plugin.getIds().length > 0 ? plugin.getIds()[0] : "";
        row.put("id", id);
        row.put("name", plugin.getName() != null ? plugin.getName() : id);
        rows.add(row);
      }
      // Fallback if plugin registry is empty (rare in tests)
      if (rows.isEmpty()) {
        for (String code :
            new String[] {
              "POSTGRESQL", "MYSQL", "H2", "ORACLE", "MSSQL", "MSSQLNATIVE", "GENERIC"
            }) {
          Map<String, String> row = new LinkedHashMap<>();
          row.put("id", code);
          row.put("name", code);
          rows.add(row);
        }
      }
      String json = new ObjectMapper().writeValueAsString(rows);
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError("Error listing database types", e);
    }
  }

  /**
   * Test a {@link HDatabaseConnection} JSON document (does not save). Body is the same shape as
   * metadata save.
   */
  @POST
  @Path("/database-connection/test/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response testDatabaseConnection(String jsonBody) {
    try {
      if (jsonBody == null || jsonBody.isBlank()) {
        return getServerError("Request body must be a database connection JSON document", false);
      }
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      JsonMetadataParser<HDatabaseConnection> parser =
          new JsonMetadataParser<>(HDatabaseConnection.class, provider);
      HDatabaseConnection connection =
          parser.loadJsonObject(
              HDatabaseConnection.class, new JsonFactory().createParser(jsonBody));
      if (connection == null) {
        return getServerError("Could not parse database connection JSON", false);
      }
      DatabaseMeta meta = connection.createDatabaseMeta();
      org.apache.hop.core.variables.Variables vars = new org.apache.hop.core.variables.Variables();
      vars.initializeFrom(null);
      String message = meta.testConnection(vars);
      if (message != null && message.toLowerCase().contains("error")) {
        return getServerError(message, false);
      }
      return Response.ok(message != null ? message : "Connection OK: " + connection.getName())
          .type(MediaType.TEXT_PLAIN)
          .build();
    } catch (Exception e) {
      return getServerError("Error testing database connection", e);
    }
  }

  /**
   * Save a metadata element.
   *
   * <p>Body is raw JSON (not {@link IHopMetadata}): Jackson cannot instantiate the abstract
   * interface. We resolve the concrete class from the metadata key and parse with {@link
   * JsonMetadataParser}, matching how connector/component save paths work.
   *
   * @param key metadata type key (e.g. {@code presentation}, {@code theme})
   * @param jsonBody JSON document for the element
   * @return saved element name
   */
  @POST
  @Path("/{key}/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
  public Response saveElement(@PathParam("key") String key, String jsonBody) {
    try {
      if (jsonBody == null || jsonBody.isBlank()) {
        return getServerError("Request body must be a JSON metadata document", false);
      }
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      Class<IHopMetadata> metadataClass = provider.getMetadataClassForKey(key);
      IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(metadataClass);
      IHopMetadata metadata = null;
      try {
        JsonMetadataParser<IHopMetadata> parser =
            new JsonMetadataParser<>(metadataClass, provider);
        com.fasterxml.jackson.core.JsonParser jsonParser =
            new JsonFactory().createParser(jsonBody);
        metadata = parser.loadJsonObject(metadataClass, jsonParser);
      } catch (Exception ignored) {
      }

      if (metadata == null) {
        try {
          metadata = new ObjectMapper().readValue(jsonBody, metadataClass);
        } catch (Exception ex) {
          return getServerError("Could not parse metadata JSON for key " + key + ": " + ex.getMessage(), ex);
        }
      }

      if (metadata == null || metadata.getName() == null || metadata.getName().isBlank()) {
        return getServerError("JSON must include a non-empty name", false);
      }
      // Full presentation replace (properties panel): snapshot before overwrite
      String beforePresentationJson = null;
      boolean existed = false;
      try {
        existed = serializer.exists(metadata.getName());
      } catch (Exception ignored) {
        existed = false;
      }
      if ("presentation".equals(key) && existed) {
        try {
          beforePresentationJson = snapshotPresentationByName(metadata.getName());
        } catch (Exception ignored) {
          beforePresentationJson = null;
        }
      }
      serializer.save(metadata);
      if (beforePresentationJson != null) {
        recordPresentationUndo(metadata.getName(), beforePresentationJson);
      }
      if ("presentation".equals(key)) {
        org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
            .invalidatePresentation(metadata.getName());
      }
      if ("connector".equals(key)
          || "theme".equals(key)
          || "hopper-database-connection".equals(key)
          || "pictorial-series".equals(key)) {
        // Connector/theme/DB/series changes can affect many presentations — drop all layout snapshots
        org.hopper.presentation.layout.HPresentationLayoutCache.getInstance().invalidateAll();
      }
      if ("hopper-database-connection".equals(key)) {
        // Drop pooled JDBC connections so new credentials/secrets take effect
        HDatabaseConnectionPool.invalidate(metadata.getName());
      }
      hopperRest.getLog().logBasic("Saved metadata key='" + key + "' name='" + metadata.getName() + "'");
      HAuditEmitter.getInstance()
          .emitSafely(
              HUsageAudit.metadataChange(
                  existed ? HAuditEventType.METADATA_UPDATE : HAuditEventType.METADATA_CREATE,
                  key,
                  metadata.getName(),
                  HSecurityContext.getPrincipal()));
      return Response.ok().entity(metadata.getName()).type(MediaType.TEXT_PLAIN).build();
    } catch (Exception e) {
      return getServerError("Error saving metadata for key " + key, e);
    }
  }

  /**
   * List presentation details.
   *
   * @return
   * @throws HopException
   */
  @GET
  @Path("/presentations/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listPresentations() throws HopException {
    IHopMetadataProvider provider = hopperRest.getMetadataProvider();
    IHopMetadataSerializer<HPresentation> serializer =
        provider.getSerializer(HPresentation.class);
    List<PresentationResponse> list = new ArrayList<>();
    List<String> names = serializer.listObjectNames();
    for (String name : names) {
      HPresentation presentation = serializer.load(name);
      list.add(
          new PresentationResponse(
              presentation.getName(),
              presentation.getDescription(),
              presentation.getVirtualPath()));
    }
    try {
      String json = new ObjectMapper().writeValueAsString(list);
      return Response.ok()
          .entity(json)
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return Response.serverError()
          .entity("Error listing presentation metadata details " + Const.getStackTracker(e))
          .type(MediaType.TEXT_PLAIN)
          .encoding("UTF-8")
          .build();
    }
  }

  /**
   * Modify a component on a page in a presentation.
   *
   * @param request the component modification request
   * @return The name of the presentation that's been modified or an error.
   */
  @POST
  @Path("modify/component/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response modifyComponent(ModifyComponentRequest request) {
    try {
      ILogChannel log = hopperRest.getLog();

      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      IHopMetadataSerializer<HPresentation> serializer =
          provider.getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(request.getPresentationName());
      if (presentation == null) {
        throw new HException("Couldn't find presentation " + request.getPresentationName());
      }

      String pageRole =
          request.getPageRole() == null || request.getPageRole().isBlank()
              ? "page"
              : request.getPageRole().trim().toLowerCase();

      HPage preferredPage = null;
      if ("header".equals(pageRole)) {
        preferredPage = presentation.getHeader();
        if (preferredPage == null) {
          throw new HException(
              "Presentation " + request.getPresentationName() + " has no header page");
        }
      } else if ("footer".equals(pageRole)) {
        preferredPage = presentation.getFooter();
        if (preferredPage == null) {
          throw new HException(
              "Presentation " + request.getPresentationName() + " has no footer page");
        }
      } else if (request.getLogicalPageNumber() >= 0
          && request.getLogicalPageNumber() < presentation.getPages().size()) {
        preferredPage = presentation.getPages().get(request.getLogicalPageNumber());
      }

      // Resolve top-level or nested (group/composite) components by metadata or drawn name
      ComponentLookup.Found found =
          ComponentLookup.find(presentation, preferredPage, request.getOldComponentName());
      if (found == null) {
        throw new HException(
            "Unable to find component to replace '"
                + request.getOldComponentName()
                + "' on logical page number "
                + request.getLogicalPageNumber()
                + " (role="
                + pageRole
                + ") of presentation "
                + request.getPresentationName()
                + " (also searched nested group/composite templates).");
      }

      String beforeJson = snapshotPresentation(presentation);

      // De-serialize the component JSON
      //
      JsonMetadataParser<HComponent> parser =
          new JsonMetadataParser<>(HComponent.class, hopperRest.getMetadataProvider());

      JsonFactory jsonFactory = new JsonFactory();
      com.fasterxml.jackson.core.JsonParser jsonParser =
          jsonFactory.createParser(request.getHopperComponentJson());

      HComponent hopperComponent = parser.loadJsonObject(HComponent.class, jsonParser);
      ComponentLookup.replace(found, hopperComponent);

      serializer.save(presentation);
      recordPresentationUndo(request.getPresentationName(), beforeJson);
      // Component metadata changed: drop layout snapshots for this presentation (siblings still
      // re-validate via content fingerprint; dependents re-layout when geometry changes).
      org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
          .invalidateComponent(
              request.getPresentationName(),
              hopperComponent != null && hopperComponent.getName() != null
                  ? hopperComponent.getName()
                  : request.getOldComponentName());
      // Also drop old name if renamed
      if (request.getOldComponentName() != null
          && hopperComponent != null
          && hopperComponent.getName() != null
          && !request.getOldComponentName().equals(hopperComponent.getName())) {
        org.hopper.presentation.layout.HPresentationLayoutCache.getInstance()
            .invalidateComponent(request.getPresentationName(), request.getOldComponentName());
      }

      log.logBasic(
          "modify/component: modified presentation " + request.getPresentationName() + " saved.");

      return Response.ok().entity(request.getPresentationName()).build();
    } catch (Exception e) {
      return getServerError(
          "Error modifying component in presentation " + request.getPresentationName(), e);
    }
  }

  /**
   * List metadata elements for admin / home tables with name, description, virtualPath, and
   * type-specific extras.
   *
   * <p>Returns {@code [{ "name", "description", "virtualPath", … }, …]} sorted by name.
   *
   * <p>Type extras: {@code connector} → {@code pluginId}; {@code hopper-database-connection} → {@code
   * databaseTypeCode}.
   */
  @GET
  @Path("/summary/{key}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listMetadataSummaries(@PathParam("key") String key) {
    try {
      List<Map<String, Object>> rows = buildMetadataSummaries(key);
      String json = new ObjectMapper().writeValueAsString(rows);
      return Response.ok(json).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (Exception e) {
      return getServerError("Error listing metadata summaries for key " + key, e);
    }
  }

  /**
   * List connector metadata with plugin type for the admin table (icons, tooltips).
   *
   * <p>Delegates to {@link #listMetadataSummaries(String)} for key {@code connector}. Returns
   * {@code [{ "name", "description", "virtualPath", "pluginId" }, ...]}.
   */
  @GET
  @Path("/connectors/summary/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listConnectorSummaries() {
    return listMetadataSummaries("connector");
  }

  /**
   * Build summary rows for a metadata key: name, description, virtualPath, plus type extras.
   */
  private List<Map<String, Object>> buildMetadataSummaries(String key) throws Exception {
    IHopMetadataProvider provider = hopperRest.getMetadataProvider();
    Class<IHopMetadata> metadataClass = provider.getMetadataClassForKey(key);
    IHopMetadataSerializer<IHopMetadata> serializer = provider.getSerializer(metadataClass);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (String name : serializer.listObjectNames()) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", name);
      row.put("description", "");
      row.put("virtualPath", "");
      try {
        IHopMetadata metadata = serializer.load(name);
        if (metadata != null) {
          if (metadata.getName() != null && !metadata.getName().isBlank()) {
            row.put("name", metadata.getName());
          }
          String virtualPath = metadata.getVirtualPath();
          row.put("virtualPath", virtualPath != null ? virtualPath : "");
          String description = readDescription(metadata);
          row.put("description", description != null ? description : "");
          putTypeExtras(key, metadata, row);
        }
      } catch (Exception loadEx) {
        hopperRest
            .getLog()
            .logBasic(
                "metadata/summary/" + key + ": could not load '" + name + "': " + loadEx.getMessage());
      }
      rows.add(row);
    }
    rows.sort(
        Comparator.comparing(
            r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
    return rows;
  }

  private static String readDescription(IHopMetadata metadata) {
    if (metadata instanceof HPresentation p) {
      return p.getDescription();
    }
    if (metadata instanceof org.hopper.presentation.theme.HTheme t) {
      return t.getDescription();
    }
    if (metadata instanceof org.hopper.presentation.component.types.pictorial.HPictorialSeries s) {
      return s.getDescription();
    }
    return "";
  }

  private static void putTypeExtras(String key, IHopMetadata metadata, Map<String, Object> row) {
    if ("connector".equals(key) && metadata instanceof HConnector connector) {
      String pluginId = null;
      if (connector.getConnector() != null) {
        pluginId = connector.getConnector().getPluginId();
      }
      row.put("pluginId", pluginId);
    } else if ("hopper-database-connection".equals(key)
        && metadata instanceof HDatabaseConnection connection) {
      row.put("databaseTypeCode", connection.getDatabaseTypeCode());
      row.put("hostname", connection.getHostname() != null ? connection.getHostname() : "");
      row.put("port", connection.getPort() != null ? connection.getPort() : "");
      row.put(
          "databaseName",
          connection.getDatabaseName() != null ? connection.getDatabaseName() : "");
    } else if ("pictorial-series".equals(key)
        && metadata instanceof org.hopper.presentation.component.types.pictorial.HPictorialSeries s) {
      row.put("renderMode", s.getRenderMode() != null ? s.getRenderMode().name() : "STEP_IMAGES");
    }
  }

  /**
   * Load a connector as Hop metadata JSON (plugin id as nested map key), suitable for the generated
   * form editor.
   */
  @GET
  @Path("/connector-json/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getConnectorJson(@PathParam("name") String name) {
    try {
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      IHopMetadataSerializer<HConnector> serializer =
          provider.getSerializer(HConnector.class);
      HConnector connector = serializer.load(name);
      if (connector == null) {
        throw new HException("Connector not found: " + name);
      }
      JsonMetadataParser<HConnector> parser =
          new JsonMetadataParser<>(HConnector.class, provider);
      org.json.simple.JSONObject json = parser.getJsonObject(connector);
      return Response.ok()
          .entity(json.toJSONString())
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error loading connector JSON for " + name, e);
    }
  }

  /**
   * Save a connector metadata element (create or update). Supports rename by deleting the old name
   * when {@code oldConnectorName} differs from the JSON name.
   */
  @POST
  @Path("modify/connector/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response modifyConnector(ModifyConnectorRequest request) {
    try {
      ILogChannel log = hopperRest.getLog();
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      IHopMetadataSerializer<HConnector> serializer =
          provider.getSerializer(HConnector.class);

      JsonMetadataParser<HConnector> parser =
          new JsonMetadataParser<>(HConnector.class, provider);
      JsonFactory jsonFactory = new JsonFactory();
      com.fasterxml.jackson.core.JsonParser jsonParser =
          jsonFactory.createParser(request.getHopperConnectorJson());
      HConnector connector = parser.loadJsonObject(HConnector.class, jsonParser);
      if (connector == null || connector.getName() == null || connector.getName().isBlank()) {
        throw new HException("Connector JSON must include a non-empty name");
      }

      String oldName = request.getOldConnectorName();
      if (oldName != null
          && !oldName.isBlank()
          && !oldName.equals(connector.getName())) {
        // Use resilient delete (corrupt old JSON must not block rename)
        try {
          if (serializer.exists(oldName) || metadataJsonFileExists("connector", oldName)) {
            deleteMetadataElement("connector", oldName);
            log.logBasic("modify/connector: deleted renamed connector '" + oldName + "'");
          }
        } catch (Exception renameDeleteEx) {
          log.logError(
              "modify/connector: could not delete old name '" + oldName + "' during rename",
              renameDeleteEx);
          throw renameDeleteEx;
        }
      }

      serializer.save(connector);
      log.logBasic("modify/connector: saved connector '" + connector.getName() + "'");
      return Response.ok().entity(connector.getName()).build();
    } catch (Exception e) {
      return getServerError("Error saving connector metadata", e);
    }
  }
}
