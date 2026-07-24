package org.hopper.presentation.connector.types.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.HJson;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.audit.lineage.HConnectorRun;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.security.HPrincipal;
import org.hopper.security.HSecurityContext;
import lombok.Getter;
import lombok.Setter;

/**
 * Retrieves JSON from an HTTP endpoint and maps array elements to rows.
 *
 * <p>Uses the JDK {@link HttpClient} and Jackson (same stack as presentation JSON). Prefer binding
 * the service URL to trusted endpoints only (SSRF risk if the URL is user-controlled).
 */
@JsonDeserialize(as = HRestConnector.class)
@HConnectorPlugin(
    id = "HRestConnector",
    name = "REST",
    description = "This connector retrieves and parses JSON data from a REST service",
    image = "ui/images/connectors/rest.svg")
@Getter
@Setter
public class HRestConnector extends HBaseConnector implements IHConnector {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

  @HWidgetElement(
      order = "10000-url",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "URL")
  @HopMetadataProperty
  private String url;

  @HWidgetElement(
      order = "10100-path",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Path")
  @HopMetadataProperty
  private String path;

  @HWidgetElement(
      order = "10200-body",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Request body")
  @HopMetadataProperty
  private String body;

  @HWidgetElement(
      order = "10250-useCallerBearer",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Use caller Bearer token",
      toolTip =
          "Send Authorization: Bearer from the logged-in user (Ship/Harbor APIs). Requires OAuth session or Bearer login.")
  @HopMetadataProperty
  private boolean useCallerBearer;

  @HWidgetElement(
      order = "10260-authorizationHeader",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Authorization header",
      toolTip =
          "Optional static header value, e.g. Bearer ${SERVICE_TOKEN}. Used when Use caller Bearer is off or no user token is available.")
  @HopMetadataProperty
  private String authorizationHeader;

  @HWidgetElement(
      order = "10300-rowsElement",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Rows JSON path",
      toolTip =
          "JSON object property that holds the row array. Leave empty or use '.' for a root JSON array (e.g. Ship GET /api/runs).")
  @HopMetadataProperty
  private String rowsElement;

  /**
   * Maps JSON object properties to Hop value metas. Edited as a typed list ({@code itemKind=jsonField})
   * in the browser form.
   */
  @HWidgetElement(
      order = "10400-fields",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Output fields",
      toolTip = "JSON tags mapped to row columns (tag, name, Hop type, format)")
  @HopMetadataProperty(key = "fields")
  private List<JsonField> fields;

  public HRestConnector() {
    super("HRestConnector");
    this.fields = new ArrayList<>();
  }

  public HRestConnector(HRestConnector c) {
    this();
    this.url = c.url;
    this.path = c.path;
    this.body = c.body;
    this.useCallerBearer = c.useCallerBearer;
    this.authorizationHeader = c.authorizationHeader;
    this.rowsElement = c.rowsElement;
    c.fields.forEach(f -> this.fields.add(new JsonField(f)));
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    try {
      IRowMeta rowMeta = new RowMeta();
      for (JsonField field : fields) {
        rowMeta.addValueMeta(field.createValueMeta());
      }
      return rowMeta;
    } catch (Exception e) {
      throw new HException("Error describing output of the REST connector", e);
    }
  }

  @Override
  public HRestConnector clone() {
    return new HRestConnector(this);
  }

  @Override
  protected void doStartStreaming(IDataContext dataContext) throws HException {
    IVariables variables = dataContext.getVariables();
    IRowMeta rowMeta = describeOutput(dataContext);

    String base = Const.NVL(variables.resolve(url), "");
    String extra = Const.NVL(variables.resolve(path), "");
    String fullUrl = base + extra;

    try {
      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(fullUrl))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/json");

      applyAuthorization(requestBuilder, variables);

      if (StringUtils.isNotEmpty(body)) {
        String resolvedBody = variables.resolve(body);
        requestBuilder
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(resolvedBody, StandardCharsets.UTF_8));
      } else {
        requestBuilder.GET();
      }

      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

      int statusCode = response.statusCode();
      if (statusCode < 200 || statusCode >= 300) {
        throw new HException(
            "REST call failed with HTTP error code : " + statusCode + " for URL " + fullUrl);
      }

      String json = response.body();
      ObjectMapper mapper = HJson.createMapper();
      JsonNode root;
      try {
        root = mapper.readTree(json);
      } catch (Exception e) {
        throw new HException("Error parsing JSON body: " + json, e);
      }

      String realRowsElement =
          variables != null ? Const.NVL(variables.resolve(rowsElement), "") : Const.NVL(rowsElement, "");
      ArrayNode rowElements = resolveRowArray(root, realRowsElement, mapper, fullUrl, json);

      for (JsonNode rowObject : rowElements) {
        if (!(rowObject instanceof ObjectNode)) {
          throw new HException("Expected each row to be a JSON object in element '" + realRowsElement + "'");
        }

        Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
        for (int i = 0; i < rowMeta.size(); i++) {
          IValueMeta valueMeta = rowMeta.getValueMeta(i);
          JsonField field = fields.get(i);
          JsonNode valueNode = rowObject.get(field.getTag());

          if (valueNode != null && !valueNode.isNull()) {
            switch (valueMeta.getType()) {
              case IValueMeta.TYPE_STRING:
                rowData[i] = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
                break;
              case IValueMeta.TYPE_INTEGER:
                rowData[i] = valueNode.canConvertToLong()
                    ? valueNode.asLong()
                    : Long.parseLong(valueNode.asText());
                break;
              case IValueMeta.TYPE_NUMBER:
                rowData[i] = valueNode.isNumber()
                    ? valueNode.asDouble()
                    : Double.parseDouble(valueNode.asText());
                break;
              case IValueMeta.TYPE_BOOLEAN:
                rowData[i] = valueNode.asBoolean();
                break;
              default:
                throw new HException(
                    "Data type "
                        + valueMeta.getTypeDesc()
                        + " isn't supported yet for tag: '"
                        + field.getTag()
                        + "', value: "
                        + valueNode);
            }
          }
        }

        passToRowListeners(rowMeta, rowData);
      }

      outputDone();
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error getting data from REST service URL " + fullUrl, e);
    }
  }

  @Override
  public void waitUntilFinished() throws HException {
    // Synchronous connector — nothing to wait for.
  }


  @Getter
  @Setter
  public static final class JsonField {

    /** Hop type names offered in the form editor (must match {@link ValueMetaFactory}). */
    public static final String[] FORM_TYPE_NAMES = {
      "String",
      "Integer",
      "Number",
      "BigNumber",
      "Boolean",
      "Date",
      "Timestamp",
      "Binary",
      "Internet Address"
    };

    @HWidgetElement(
        order = "100-tag",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "JSON tag")
    @HopMetadataProperty
    private String tag;

    @HWidgetElement(
        order = "200-name",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Field name")
    @HopMetadataProperty
    private String name;

    @HWidgetElement(
        order = "300-type",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.COMBO,
        label = "Type",
        comboValuesMethod = "getFormTypeNames")
    @HopMetadataProperty
    private String type;

    @HWidgetElement(
        order = "400-formatMask",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Format mask")
    @HopMetadataProperty
    private String formatMask;

    @HWidgetElement(
        order = "500-length",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Length")
    @HopMetadataProperty
    private String length;

    @HWidgetElement(
        order = "600-precision",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Precision")
    @HopMetadataProperty
    private String precision;

    @HWidgetElement(
        order = "700-decimal",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Decimal symbol")
    @HopMetadataProperty
    private String decimal;

    @HWidgetElement(
        order = "800-grouping",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Grouping symbol")
    @HopMetadataProperty
    private String grouping;

    public JsonField() {}

    public JsonField(JsonField f) {
      this.tag = f.tag;
      this.name = f.name;
      this.type = f.type;
      this.formatMask = f.formatMask;
      this.length = f.length;
      this.precision = f.precision;
      this.decimal = f.decimal;
      this.grouping = f.grouping;
    }

    public JsonField(String name, String type) {
      this();
      this.name = name;
      this.tag = name;
      this.type = type;
    }

    /** Combo options for the type field (invoked via {@code comboValuesMethod}). */
    public String[] getFormTypeNames() {
      return FORM_TYPE_NAMES;
    }

    public IValueMeta createValueMeta() throws HopPluginException {
      int hopType = ValueMetaFactory.getIdForValueMeta(type);
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(Const.NVL(name, tag), hopType);
      valueMeta.setLength(Const.toInt(length, -1));
      valueMeta.setPrecision(Const.toInt(precision, -1));
      valueMeta.setConversionMask(formatMask);
      valueMeta.setDecimalSymbol(decimal);
      valueMeta.setGroupingSymbol(grouping);
      return valueMeta;
    }
  }

  @Override
  protected void enrichConnectorRun(HConnectorRun run, IDataContext dataContext) {
    String resolved = url;
    if (dataContext != null && dataContext.getVariables() != null && url != null) {
      resolved = dataContext.getVariables().resolve(url);
    }
    run.getAttributes().put("url", resolved);
    run.getAttributes().put("useCallerBearer", Boolean.toString(useCallerBearer));
  }

  /**
   * Resolve the array of row objects from a JSON root. Supports:
   *
   * <ul>
   *   <li>Root JSON array when {@code rowsElement} is blank, {@code .}, or {@code $}
   *   <li>Object property holding an array (or a single object treated as one row)
   * </ul>
   */
  static ArrayNode resolveRowArray(
      JsonNode root, String rowsElement, ObjectMapper mapper, String fullUrl, String json)
      throws HException {
    if (root == null || root.isNull()) {
      throw new HException("Empty JSON body from URL " + fullUrl);
    }
    boolean rootArray =
        rowsElement == null
            || rowsElement.isBlank()
            || ".".equals(rowsElement.trim())
            || "$".equals(rowsElement.trim());
    if (rootArray) {
      if (root instanceof ArrayNode arrayNode) {
        return arrayNode;
      }
      if (root instanceof ObjectNode) {
        ArrayNode single = mapper.createArrayNode();
        single.add(root);
        return single;
      }
      throw new HException("Expected a JSON array at root for URL " + fullUrl);
    }

    if (!(root instanceof ObjectNode)) {
      throw new HException(
          "Expected a JSON object as REST response root for URL "
              + fullUrl
              + " (or leave rows path empty for a root array)");
    }
    JsonNode elements = root.get(rowsElement.trim());
    if (elements == null || elements.isNull()) {
      throw new HException("Unable to find rows element '" + rowsElement + "' in JSON: " + json);
    }
    if (elements instanceof ObjectNode) {
      ArrayNode single = mapper.createArrayNode();
      single.add(elements);
      return single;
    }
    if (elements instanceof ArrayNode) {
      return (ArrayNode) elements;
    }
    throw new HException("Expected an array of rows in JSON element '" + rowsElement + "'");
  }

  void applyAuthorization(HttpRequest.Builder requestBuilder, IVariables variables) {
    String header = null;
    if (useCallerBearer) {
      HPrincipal principal = HSecurityContext.getPrincipal();
      if (principal != null) {
        String token = principal.getBearerToken();
        if (token != null && !token.isBlank()) {
          header = "Bearer " + token.trim();
        }
      }
    }
    if (header == null && StringUtils.isNotEmpty(authorizationHeader)) {
      String resolved =
          variables != null ? variables.resolve(authorizationHeader) : authorizationHeader;
      if (StringUtils.isNotBlank(resolved)) {
        header = resolved.trim();
        // Allow bare token without scheme
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)
            && !header.regionMatches(true, 0, "Basic ", 0, 6)) {
          header = "Bearer " + header;
        }
      }
    }
    if (header != null) {
      requestBuilder.header("Authorization", header);
    }
  }
}
