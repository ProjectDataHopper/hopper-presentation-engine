package org.hopper.rest.resources;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.resolver.IVariableResolver;
import org.apache.hop.core.variables.resolver.VariableResolver;
import org.apache.hop.core.variables.resolver.VariableResolverPluginType;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.hopper.core.gui.form.GuiFormSchema;
import org.hopper.core.gui.form.GuiFormSchemaBuilder;
import org.hopper.core.gui.plugin.HGuiRegistry;
import org.hopper.security.HAccessDeniedException;
import org.hopper.security.HAction;
import org.hopper.security.HSecurityContext;

/**
 * Admin API for Hop variable-resolver plugins and metadata.
 *
 * <p>All persistence goes through the process {@link IHopMetadataProvider} (a {@code
 * JsonMetadataProvider} created in {@code HRest}) via {@link IHopMetadataSerializer} and {@link
 * JsonMetadataParser} — the same path as {@code /metadata/variable-resolver/…}.
 *
 * <p>Requires {@link HAction#SECURITY_ADMIN}.
 */
@Path("admin/variable-resolvers")
public class AdminVariableResolverResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  @GET
  @Path("/plugins")
  @Produces(MediaType.APPLICATION_JSON)
  public Response listPlugins() {
    try {
      requireAdmin();
      PluginRegistry registry = PluginRegistry.getInstance();
      List<IPlugin> plugins =
          new ArrayList<>(registry.getPlugins(VariableResolverPluginType.class));
      plugins.sort(
          Comparator.comparing(
              p -> p.getName() != null ? p.getName() : "", String.CASE_INSENSITIVE_ORDER));
      List<Map<String, Object>> rows = new ArrayList<>();
      for (IPlugin plugin : plugins) {
        Map<String, Object> row = new LinkedHashMap<>();
        String id =
            plugin.getIds() != null && plugin.getIds().length > 0 ? plugin.getIds()[0] : "";
        row.put("id", id);
        row.put("name", plugin.getName() != null ? plugin.getName() : id);
        row.put("description", plugin.getDescription() != null ? plugin.getDescription() : "");
        String className = plugin.getClassMap().get(IVariableResolver.class);
        row.put("className", className != null ? className : "");
        rows.add(row);
      }
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("plugins", rows);
      body.put("count", rows.size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing variable resolver plugins", e);
    }
  }

  @GET
  @Path("/schema/{pluginId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response schema(@PathParam("pluginId") String pluginId) {
    try {
      requireAdmin();
      Class<?> clazz = loadResolverClass(pluginId);
      if (clazz == null) {
        return getServerError("Variable resolver plugin not found: " + pluginId, false);
      }
      IPlugin plugin =
          PluginRegistry.getInstance()
              .findPluginWithId(VariableResolverPluginType.class, pluginId);
      String name = plugin != null && plugin.getName() != null ? plugin.getName() : pluginId;
      String description =
          plugin != null && plugin.getDescription() != null ? plugin.getDescription() : "";

      HGuiRegistry.getInstance().registerClass(clazz);
      GuiFormSchema schema =
          new GuiFormSchemaBuilder().buildClassSchema(pluginId, name, description, clazz);
      return Response.ok(MAPPER.writeValueAsString(schema))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error building variable resolver schema for " + pluginId, e);
    }
  }

  /**
   * Load a variable resolver from metadata via {@link IHopMetadataSerializer#load(String)} and
   * return both Hop JSON and a flattened form view for the admin UI.
   */
  @GET
  @Path("/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response load(@PathParam("name") String name) {
    try {
      requireAdmin();
      if (StringUtils.isBlank(name)) {
        return getServerError("name is required", false);
      }
      IHopMetadataSerializer<VariableResolver> serializer = serializer();
      VariableResolver meta = serializer.load(name);
      if (meta == null) {
        return getServerError("Variable resolver not found: " + name, false);
      }
      return Response.ok(MAPPER.writeValueAsString(toAdminView(meta)))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error loading variable resolver " + name, e);
    }
  }

  /** List resolver names from the metadata provider. */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response list() {
    try {
      requireAdmin();
      List<String> names = new ArrayList<>(serializer().listObjectNames());
      names.sort(String.CASE_INSENSITIVE_ORDER);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("names", names);
      body.put("count", names.size());
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error listing variable resolvers", e);
    }
  }

  /**
   * Build full Hop metadata JSON for a variable resolver from a simplified admin payload, using
   * {@link JsonMetadataParser} (no direct file IO).
   *
   * <p>Body: {@code { "name", "description", "pluginId", "fields": { fieldName: value, … } }}
   */
  @POST
  @Path("/serialize")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response serialize(String jsonBody) {
    try {
      requireAdmin();
      VariableResolver meta = parseAdminForm(jsonBody);
      String hopJson = toHopJson(meta);
      return Response.ok(hopJson).type(MediaType.APPLICATION_JSON_TYPE).encoding("UTF-8").build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return getServerError(e.getMessage(), false);
    } catch (Exception e) {
      return getServerError("Error serializing variable resolver", e);
    }
  }

  /**
   * Test a resolver configuration without saving. Uses the same {@link JsonMetadataParser}
   * materialization as save.
   */
  @POST
  @Path("/test")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response test(String jsonBody) {
    try {
      requireAdmin();
      Map<String, Object> in =
          MAPPER.readValue(
              jsonBody != null ? jsonBody : "{}", new TypeReference<Map<String, Object>>() {});
      if (StringUtils.isBlank(stringVal(in.get("name")))) {
        in.put("name", "_test");
        jsonBody = MAPPER.writeValueAsString(in);
      }
      VariableResolver meta = parseAdminForm(jsonBody);
      String rawArgument = stringVal(in.get("argument"));
      // Test calls IVariableResolver.resolve(secretPath) directly — not Variables.resolve().
      // Accept either a bare secret id ("my-secret") or a full expression "#{gsm:my-secret}" /
      // "#{gsm:my-secret:jsonKey}" and extract the path (and optional JSON field key).
      ResolvedTestArgument parsedArg = parseTestArgument(rawArgument, meta.getName());
      IVariableResolver resolver = meta.getIResolver();
      if (resolver == null) {
        return getServerError("No resolver instance", false);
      }
      resolver.init();
      String value = resolver.resolve(parsedArg.secretPath, hopperRest.getVariables());
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("secretPath", parsedArg.secretPath);
      if (parsedArg.jsonKey != null) {
        body.put("jsonKey", parsedArg.jsonKey);
      }
      if (StringUtils.isNotBlank(rawArgument) && !rawArgument.equals(parsedArg.secretPath)) {
        body.put("argumentNormalizedFrom", rawArgument);
      }
      if (value == null || value.isEmpty()) {
        body.put("ok", false);
        body.put(
            "error",
            "Resolver returned null/empty for secretPath='"
                + parsedArg.secretPath
                + "' (check secret id in GSM, project id, ADC credentials, and network)");
        body.put("value", null);
      } else {
        // Optional second segment of #{name:path:jsonKey} — pick a JSON field like Hop Variables does
        if (StringUtils.isNotBlank(parsedArg.jsonKey)) {
          value = extractJsonField(value, parsedArg.jsonKey);
          if (value == null) {
            body.put("ok", false);
            body.put(
                "error",
                "Secret resolved but JSON key '" + parsedArg.jsonKey + "' was not found");
            body.put("value", null);
            return Response.ok(MAPPER.writeValueAsString(body))
                .type(MediaType.APPLICATION_JSON)
                .build();
          }
        }
        body.put("ok", true);
        boolean redact = value.length() > 64 || looksLikeSecret(value);
        body.put("value", redact ? redactValue(value) : value);
        body.put("masked", redact);
        body.put("length", value.length());
      }
      return Response.ok(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return getServerError(e.getMessage(), false);
    } catch (Exception e) {
      return getServerError("Error testing variable resolver", e);
    }
  }

  /**
   * Persist via {@link IHopMetadataSerializer#save}. Body is the simplified admin form; Hop JSON is
   * produced and loaded with {@link JsonMetadataParser} first so polymorphic plugin fields match
   * desktop Hop.
   */
  @POST
  @Path("/save")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response save(String jsonBody) {
    try {
      requireAdmin();
      Map<String, Object> in =
          MAPPER.readValue(
              jsonBody != null ? jsonBody : "{}", new TypeReference<Map<String, Object>>() {});
      VariableResolver meta = parseAdminForm(jsonBody);
      IHopMetadataSerializer<VariableResolver> serializer = serializer();
      boolean existed = false;
      try {
        existed = serializer.exists(meta.getName());
      } catch (Exception ignored) {
        existed = false;
      }
      serializer.save(meta);

      String previousName = stringVal(in.get("previousName"));
      if (StringUtils.isNotBlank(previousName) && !previousName.equals(meta.getName())) {
        try {
          if (serializer.exists(previousName)) {
            serializer.delete(previousName);
          }
        } catch (Exception ignored) {
          // soft-fail rename cleanup
        }
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("ok", true);
      body.put("name", meta.getName());
      body.put("existed", existed);
      // Echo Hop JSON so the UI can verify provider-round-trip shape
      body.put("metadata", MAPPER.readValue(toHopJson(meta), Object.class));
      return Response.ok(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (IllegalArgumentException e) {
      return getServerError(e.getMessage(), false);
    } catch (Exception e) {
      return getServerError("Error saving variable resolver", e);
    }
  }

  @DELETE
  @Path("/{name}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response delete(@PathParam("name") String name) {
    try {
      requireAdmin();
      if (StringUtils.isBlank(name)) {
        return getServerError("name is required", false);
      }
      IHopMetadataSerializer<VariableResolver> serializer = serializer();
      if (!serializer.exists(name)) {
        return getServerError("Variable resolver not found: " + name, false);
      }
      serializer.delete(name);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("ok", true);
      body.put("name", name);
      return Response.ok(MAPPER.writeValueAsString(body)).type(MediaType.APPLICATION_JSON).build();
    } catch (HAccessDeniedException e) {
      return getForbidden(e);
    } catch (Exception e) {
      return getServerError("Error deleting variable resolver " + name, e);
    }
  }

  // ── IHopMetadataProvider helpers ──────────────────────────────────────────

  private IHopMetadataProvider provider() {
    return hopperRest.getMetadataProvider();
  }

  private IHopMetadataSerializer<VariableResolver> serializer() throws Exception {
    return provider().getSerializer(VariableResolver.class);
  }

  private JsonMetadataParser<VariableResolver> parser() {
    return new JsonMetadataParser<>(VariableResolver.class, provider());
  }

  /**
   * Convert simplified admin form JSON into a {@link VariableResolver} by building Hop polymorphic
   * metadata JSON and loading it with {@link JsonMetadataParser} (same as desktop / metadata
   * REST).
   */
  private VariableResolver parseAdminForm(String adminJsonBody) throws Exception {
    Map<String, Object> in =
        MAPPER.readValue(
            adminJsonBody != null ? adminJsonBody : "{}",
            new TypeReference<Map<String, Object>>() {});
    String name = stringVal(in.get("name"));
    String description = stringVal(in.get("description"));
    String pluginId = stringVal(in.get("pluginId"));
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("name is required");
    }
    if (StringUtils.isBlank(pluginId)) {
      throw new IllegalArgumentException("pluginId is required");
    }
    if (PluginRegistry.getInstance()
            .findPluginWithId(VariableResolverPluginType.class, pluginId)
        == null) {
      throw new IllegalArgumentException("Variable resolver plugin not found: " + pluginId);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> fields =
        in.get("fields") instanceof Map
            ? (Map<String, Object>) in.get("fields")
            : new LinkedHashMap<>();

    // Hop polymorphic shape produced by JsonMetadataParser for IVariableResolver:
    //   "variable-resolver": { "<plugin-id>": { ...fields } }
    Map<String, Object> hopDoc = new LinkedHashMap<>();
    hopDoc.put("name", name);
    hopDoc.put("description", description != null ? description : "");
    Map<String, Object> pluginPayload = new LinkedHashMap<>();
    pluginPayload.put(pluginId, fields);
    hopDoc.put("variable-resolver", pluginPayload);

    return loadFromHopJson(MAPPER.writeValueAsString(hopDoc));
  }

  private VariableResolver loadFromHopJson(String hopJson) throws Exception {
    return parser()
        .loadJsonObject(VariableResolver.class, JSON_FACTORY.createParser(hopJson));
  }

  private String toHopJson(VariableResolver meta) throws Exception {
    return parser().getJsonObject(meta).toJSONString();
  }

  /**
   * Admin-friendly view: Hop JSON plus flattened pluginId/fields for the form (derived from the
   * provider-loaded object via {@link JsonMetadataParser#getJsonObject}).
   */
  private Map<String, Object> toAdminView(VariableResolver meta) throws Exception {
    String hopJson = toHopJson(meta);
    @SuppressWarnings("unchecked")
    Map<String, Object> hop =
        MAPPER.readValue(hopJson, new TypeReference<Map<String, Object>>() {});

    String pluginId = "";
    Map<String, Object> fields = new LinkedHashMap<>();
    Object vr = hop.get("variable-resolver");
    if (vr instanceof Map<?, ?> nested) {
      for (Map.Entry<?, ?> e : nested.entrySet()) {
        String key = e.getKey() != null ? e.getKey().toString() : "";
        Object val = e.getValue();
        if (val instanceof Map<?, ?> fieldMap) {
          pluginId = key;
          for (Map.Entry<?, ?> fe : fieldMap.entrySet()) {
            if (fe.getKey() != null) {
              fields.put(fe.getKey().toString(), fe.getValue());
            }
          }
          break;
        }
      }
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", meta.getName());
    body.put("description", meta.getDescription() != null ? meta.getDescription() : "");
    body.put("pluginId", pluginId);
    body.put("fields", fields);
    body.put("metadata", hop);
    return body;
  }

  private Class<?> loadResolverClass(String pluginId) {
    try {
      IPlugin plugin =
          PluginRegistry.getInstance()
              .findPluginWithId(VariableResolverPluginType.class, pluginId);
      if (plugin == null) {
        return null;
      }
      String className = plugin.getClassMap().get(IVariableResolver.class);
      if (className == null) {
        return null;
      }
      ClassLoader cl = PluginRegistry.getInstance().getClassLoader(plugin);
      return cl.loadClass(className);
    } catch (Exception e) {
      return null;
    }
  }

  private static String stringVal(Object o) {
    return o != null ? o.toString() : "";
  }

  /**
   * Normalize a test argument the way Hop's {@code Variables} engine would before calling the
   * plugin.
   *
   * <ul>
   *   <li>{@code my-secret} → path {@code my-secret}
   *   <li>{@code #{gsm:my-secret}} → path {@code my-secret}
   *   <li>{@code #{gsm:my-secret:password}} → path {@code my-secret}, jsonKey {@code password}
   * </ul>
   *
   * Do <strong>not</strong> pass the full {@code #{…}} string as the GSM secret id.
   */
  static ResolvedTestArgument parseTestArgument(String raw, String resolverMetadataName) {
    if (raw == null) {
      return new ResolvedTestArgument("", null);
    }
    String s = raw.trim();
    if (s.startsWith("#{") && s.endsWith("}") && s.length() > 3) {
      String inner = s.substring(2, s.length() - 1);
      int firstColon = inner.indexOf(':');
      if (firstColon < 0) {
        // #{name} with no path
        return new ResolvedTestArgument("", null);
      }
      // Drop resolver name; remainder is path[:jsonKey] (same as Variables.substituteVariableResolvers)
      String arguments = inner.substring(firstColon + 1);
      String[] parts = arguments.split(":", 2);
      String path = parts[0];
      String jsonKey = parts.length > 1 ? parts[1] : null;
      return new ResolvedTestArgument(path, jsonKey);
    }
    // Bare path, or path:jsonKey without #{ }
    if (s.contains(":") && !s.startsWith("projects/")) {
      String[] parts = s.split(":", 2);
      // Only treat as path:key when it looks like a short id, not a URL
      if (parts[0].length() > 0 && parts[0].length() < 256 && !parts[0].contains("/")) {
        return new ResolvedTestArgument(parts[0], parts[1]);
      }
    }
    return new ResolvedTestArgument(s, null);
  }

  private static String extractJsonField(String jsonOrText, String key) {
    if (StringUtils.isBlank(jsonOrText) || StringUtils.isBlank(key)) {
      return jsonOrText;
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> map =
          MAPPER.readValue(jsonOrText, new TypeReference<Map<String, Object>>() {});
      Object v = map.get(key);
      return v != null ? v.toString() : null;
    } catch (Exception e) {
      // Not JSON — ignore key
      return jsonOrText;
    }
  }

  static final class ResolvedTestArgument {
    final String secretPath;
    final String jsonKey;

    ResolvedTestArgument(String secretPath, String jsonKey) {
      this.secretPath = secretPath != null ? secretPath : "";
      this.jsonKey = StringUtils.isNotBlank(jsonKey) ? jsonKey : null;
    }
  }

  private static boolean looksLikeSecret(String value) {
    if (value == null) {
      return false;
    }
    String v = value.trim();
    return v.startsWith("Encrypted ")
        || v.startsWith("AES2 ")
        || v.startsWith("-----BEGIN")
        || v.length() > 48;
  }

  private static String redactValue(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (value.length() <= 8) {
      return "********";
    }
    return value.substring(0, 4)
        + "…"
        + value.substring(value.length() - 4)
        + " ("
        + value.length()
        + " chars)";
  }

  private void requireAdmin() throws HAccessDeniedException {
    HSecurityContext.getAuthorizationService()
        .check(HSecurityContext.getPrincipal(), HAction.SECURITY_ADMIN);
  }
}
