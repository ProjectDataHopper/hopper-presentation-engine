package org.hopper.rest.resources;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.preview.ConnectorPreviewResult;
import org.hopper.presentation.connector.preview.ConnectorPreviewSupport;
import org.hopper.presentation.connector.types.csv.HCsvConnector;
import org.hopper.presentation.connector.types.csv.HCsvConnector.CsvField;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.datacontext.PresentationDataContext;
import org.hopper.rest.render.IRendering;
import org.hopper.rest.render.RenderFactory;
import org.hopper.rest.resources.requests.ConnectorPreviewRequest;
import org.hopper.rest.resources.requests.CsvDetectLayoutRequest;
import org.hopper.rest.resources.responses.RowMetaResponse;

@Path("edit/")
public class EditPluginResource extends BaseResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Get the HTML to edit a component (annotation-generated when available, else static HTML).
   *
   * @param componentId The ID of the component to edit
   * @return
   */
  @GET
  @Path("/component/{componentId}/")
  public Response editComponent(@PathParam("componentId") String componentId) {
    try {
      return RenderFactory.getComponentPluginPage(this, componentId);
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error retrieving the HTML to edit component ID: " + componentId;
      return getServerError(errorMessage, e);
    }
  }

  /**
   * JSON form schema for a component plugin, derived from {@code @HWidgetElement} annotations.
   */
  @GET
  @Path("/schema/component/{componentId}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response componentSchema(@PathParam("componentId") String componentId) {
    try {
      return RenderFactory.getComponentPluginSchema(componentId);
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error retrieving the form schema for component ID: " + componentId;
      return getServerError(errorMessage, e);
    }
  }

  /** JSON form schema for a connector plugin. */
  @GET
  @Path("/schema/connector/{connectorId}/")
  @Produces(MediaType.APPLICATION_JSON)
  public Response connectorSchema(@PathParam("connectorId") String connectorId) {
    try {
      return RenderFactory.getConnectorPluginSchema(connectorId);
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error retrieving the form schema for connector ID: " + connectorId;
      return getServerError(errorMessage, e);
    }
  }

  /**
   * HTML form to edit a connector of the given plugin type (e.g. {@code SqlConnector}).
   * Client supplies {@code connectorJson} before evaluating load/save scripts.
   */
  @GET
  @Path("/connector/{connectorPluginId}/")
  public Response editConnector(@PathParam("connectorPluginId") String connectorPluginId) {
    try {
      return RenderFactory.getConnectorPluginPage(connectorPluginId);
    } catch (Exception e) {
      String errorMessage =
          "Unexpected error retrieving the HTML to edit connector plugin: " + connectorPluginId;
      return getServerError(errorMessage, e);
    }
  }

  /**
   * Sample input/output rows for the connector data studio from current form state (may be
   * unsaved).
   *
   * <p>Always returns HTTP 200 with a structured body ({@code ok}, {@code input}, {@code output},
   * {@code error}) for application outcomes. Malformed requests return 400/500.
   */
  @POST
  @Path("/connector/preview/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response previewConnector(ConnectorPreviewRequest request) {
    try {
      if (request == null || StringUtils.isBlank(request.getHopperConnectorJson())) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"ok\":false,\"error\":{\"summary\":\"hopperConnectorJson is required\"}}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }

      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      JsonMetadataParser<HConnector> parser =
          new JsonMetadataParser<>(HConnector.class, provider);
      HConnector connector =
          parser.loadJsonObject(
              HConnector.class,
              new JsonFactory().createParser(request.getHopperConnectorJson()));
      if (connector == null || connector.getConnector() == null) {
        ConnectorPreviewResult empty = new ConnectorPreviewResult();
        empty.setOk(false);
        empty.setMaxRows(ConnectorPreviewSupport.clampMaxRows(request.getMaxRows()));
        empty.setError(
            new ConnectorPreviewResult.PreviewError(
                "Could not parse connector JSON",
                "hopperConnectorJson must be a full Hop connector object with a plugin payload"));
        return Response.ok(MAPPER.writeValueAsString(empty))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      }

      IDataContext dataContext = buildPreviewDataContext(request.getRenderId(), provider, connector);
      int maxRows = ConnectorPreviewSupport.clampMaxRows(request.getMaxRows());
      ConnectorPreviewResult result =
          ConnectorPreviewSupport.preview(dataContext, connector, maxRows);

      return Response.ok(MAPPER.writeValueAsString(result))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      // Unexpected parse/infrastructure errors — still prefer structured 200 when possible
      try {
        ConnectorPreviewResult fail = new ConnectorPreviewResult();
        fail.setOk(false);
        fail.setMaxRows(
            ConnectorPreviewSupport.clampMaxRows(
                request != null ? request.getMaxRows() : null));
        fail.setError(
            new ConnectorPreviewResult.PreviewError(
                HPresentation.summarizeException(e),
                HPresentation.formatExceptionDetail(e)));
        return Response.ok(MAPPER.writeValueAsString(fail))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .encoding("UTF-8")
            .build();
      } catch (Exception serializeEx) {
        return getServerError("Error previewing connector", e);
      }
    }
  }

  /**
   * Describe output fields of an unsaved / inline connector definition (full Hop connector JSON).
   * Used by the chain builder to list columns available after previous steps (e.g. Aggregate →
   * Filter).
   *
   * <p>Request body matches preview: {@code hopperConnectorJson}, optional {@code renderId}.
   * Response is the same shape as {@code POST render/connector/describe/} (array of value metas).
   */
  @POST
  @Path("/connector/describe-inline/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response describeInlineConnector(ConnectorPreviewRequest request) {
    try {
      if (request == null || StringUtils.isBlank(request.getHopperConnectorJson())) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"hopperConnectorJson is required\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }

      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      JsonMetadataParser<HConnector> parser =
          new JsonMetadataParser<>(HConnector.class, provider);
      HConnector connector =
          parser.loadJsonObject(
              HConnector.class,
              new JsonFactory().createParser(request.getHopperConnectorJson()));
      if (connector == null || connector.getConnector() == null) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Could not parse connector JSON\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }

      IDataContext dataContext =
          buildPreviewDataContext(request.getRenderId(), provider, connector);
      IRowMeta rowMeta = connector.describeOutput(dataContext);
      String json = MAPPER.writeValueAsString(new RowMetaResponse(rowMeta).getValueMetaList());
      return Response.ok(json)
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (Exception e) {
      return getServerError("Error describing inline connector output", e);
    }
  }

  /**
   * Detect CSV column names and types from a file (header + first 100 data rows). Uses the form
   * values in the request body; does not persist connector metadata.
   */
  @POST
  @Path("/connector/csv/detect-layout/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response detectCsvLayout(CsvDetectLayoutRequest request) {
    try {
      if (request == null || StringUtils.isBlank(request.getFilename())) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"filename is required\"}")
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      }

      HCsvConnector csv = new HCsvConnector();
      csv.setFilename(request.getFilename());
      csv.setHeaderPresent(request.isHeaderPresent());
      csv.setSeparator(request.getSeparator());
      csv.setLocale(request.getLocale());
      csv.setGroupingSymbol(request.getGroupingSymbol());
      csv.setEncoding(request.getEncoding());

      IVariables variables = hopperRest.getVariables();
      List<CsvField> fields = csv.detectLayout(variables);

      Map<String, Object> body = new HashMap<>();
      body.put("fields", fields);
      return Response.ok(MAPPER.writeValueAsString(body))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .encoding("UTF-8")
          .build();
    } catch (HException e) {
      try {
        Map<String, Object> err = new HashMap<>();
        err.put("error", HPresentation.summarizeException(e));
        err.put("detail", HPresentation.formatExceptionDetail(e));
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(MAPPER.writeValueAsString(err))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build();
      } catch (Exception serializeEx) {
        return getServerError("Error detecting CSV layout", e);
      }
    } catch (Exception e) {
      return getServerError("Error detecting CSV layout", e);
    }
  }

  /**
   * Build a data context that resolves connectors from metadata, with the connector under edit
   * overlaid by name so unsaved form state is visible to transforms that need sibling connectors.
   */
  private IDataContext buildPreviewDataContext(
      String renderId, IHopMetadataProvider provider, HConnector underEdit)
      throws HException {
    HPresentation presentation = null;

    if (StringUtils.isNotBlank(renderId)) {
      IRendering rendering = hopperRest.getRendering(renderId);
      if (rendering != null && rendering.getPresentation() != null) {
        presentation = rendering.getPresentation();
      }
    }

    HPresentation shell = new HPresentation();
    shell.setName(presentation != null ? presentation.getName() : "_connector_preview");
    shell.setDescription(
        presentation != null ? presentation.getDescription() : "connector preview");
    if (presentation != null) {
      shell.setDefaultThemeName(presentation.getDefaultThemeName());
    }

    IDataContext base = new PresentationDataContext(shell, provider);
    if (underEdit == null
        || underEdit.getName() == null
        || underEdit.getName().isBlank()) {
      return base;
    }
    return new OverlayConnectorDataContext(base, underEdit);
  }

  /**
   * Returns the in-memory under-edit connector when the name matches; otherwise delegates to the
   * parent (metadata) context.
   */
  private static final class OverlayConnectorDataContext implements IDataContext {
    private final IDataContext parent;
    private final HConnector underEdit;

    OverlayConnectorDataContext(IDataContext parent, HConnector underEdit) {
      this.parent = parent;
      this.underEdit = underEdit;
    }

    @Override
    public HConnector getConnector(String name) throws HException {
      if (name != null && name.equalsIgnoreCase(underEdit.getName())) {
        return new HConnector(underEdit);
      }
      return parent.getConnector(name);
    }

    @Override
    public org.apache.hop.core.variables.IVariables getVariables() {
      return parent.getVariables();
    }

    @Override
    public IHopMetadataProvider getMetadataProvider() {
      return parent.getMetadataProvider();
    }
  }
}

