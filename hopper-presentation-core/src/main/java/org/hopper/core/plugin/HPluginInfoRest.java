package org.hopper.core.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.type.HComponentPluginType;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.connector.type.HConnectorPluginType;

/**
 * Lists registered Hopper component and connector plugins for REST clients.
 *
 * <p>Returns pre-serialized JSON strings so Jersey does not need entity-filtering Jackson
 * providers (avoids NPEs with jersey-media-json-jackson filtering).
 */
@Path("plugins/")
public class HPluginInfoRest {

  public static final String DEFAULT_CONNECTOR_IMAGE = "ui/images/connectors/default.svg";
  public static final String DEFAULT_COMPONENT_IMAGE = "ui/images/components/default.svg";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static List<HPluginDescription> componentPlugins;
  private static List<HPluginDescription> connectorPlugins;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("components")
  public Response listComponents() {
    try {
      if (componentPlugins == null) {
        componentPlugins =
            getPluginDescriptions(HComponentPluginType.class, IHComponent.class);
      }
      return json(componentPlugins);
    } catch (Exception e) {
      return Response.serverError()
          .entity("Error listing component plugins: " + e.getMessage())
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("connectors")
  public Response listConnectors() {
    try {
      if (connectorPlugins == null) {
        connectorPlugins =
            getPluginDescriptions(HConnectorPluginType.class, IHConnector.class);
      }
      return json(connectorPlugins);
    } catch (Exception e) {
      return Response.serverError()
          .entity("Error listing connector plugins: " + e.getMessage())
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  /**
   * Stream the SVG (or other image) declared on a connector plugin's {@code image} annotation.
   * Looked up by plugin id only (no free-form classpath paths from the client).
   */
  @GET
  @Path("connectors/{pluginId}/image")
  public Response connectorImage(@PathParam("pluginId") String pluginId) {
    return pluginImage(
        pluginId, HConnectorPluginType.class, DEFAULT_CONNECTOR_IMAGE, "connector");
  }

  /**
   * Stream the SVG declared on a component plugin's {@code image} annotation. Looked up by plugin
   * id only.
   */
  @GET
  @Path("components/{pluginId}/image")
  public Response componentImage(@PathParam("pluginId") String pluginId) {
    return pluginImage(
        pluginId, HComponentPluginType.class, DEFAULT_COMPONENT_IMAGE, "component");
  }

  private Response pluginImage(
      String pluginId,
      Class<? extends IPluginType> pluginTypeClass,
      String defaultImage,
      String kindLabel) {
    try {
      if (StringUtils.isBlank(pluginId)) {
        return Response.status(Response.Status.BAD_REQUEST).entity("pluginId required").build();
      }
      PluginRegistry registry = PluginRegistry.getInstance();
      IPlugin plugin = registry.findPluginWithId(pluginTypeClass, pluginId);
      String imagePath = null;
      InputStream in = null;
      if (plugin != null) {
        imagePath = plugin.getImageFile();
        ClassLoader classLoader = registry.getClassLoader(plugin);
        in = openImageStream(classLoader, imagePath);
      }
      if (in == null) {
        imagePath = defaultImage;
        in = HPluginInfoRest.class.getClassLoader().getResourceAsStream(defaultImage);
      }
      if (in == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("No image for " + kindLabel + " plugin: " + pluginId)
            .type(MediaType.TEXT_PLAIN)
            .build();
      }
      return Response.ok(in)
          .type(mediaTypeForPath(imagePath))
          .header("Cache-Control", "public, max-age=3600")
          .build();
    } catch (Exception e) {
      return Response.serverError()
          .entity("Error loading " + kindLabel + " image: " + e.getMessage())
          .type(MediaType.TEXT_PLAIN)
          .build();
    }
  }

  private static InputStream openImageStream(ClassLoader classLoader, String imagePath) {
    if (StringUtils.isBlank(imagePath) || classLoader == null) {
      return null;
    }
    // Deny absolute / parent traversal
    String path = imagePath.replace('\\', '/');
    if (path.startsWith("/") || path.contains("..")) {
      return null;
    }
    return classLoader.getResourceAsStream(path);
  }

  private static String mediaTypeForPath(String path) {
    String lower = path == null ? "" : path.toLowerCase();
    if (lower.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (lower.endsWith(".gif")) {
      return "image/gif";
    }
    return "application/octet-stream";
  }

  private static Response json(Object value) throws Exception {
    return Response.ok(MAPPER.writeValueAsString(value))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .encoding("UTF-8")
        .build();
  }

  private List<HPluginDescription> getPluginDescriptions(
      Class<? extends IPluginType> pluginTypeClass, Class<?> interfaceClass) {
    PluginRegistry registry = PluginRegistry.getInstance();

    List<IPlugin> plugins = registry.getPlugins(pluginTypeClass);
    List<HPluginDescription> list = new ArrayList<>();
    for (IPlugin plugin : plugins) {
      list.add(
          new HPluginDescription(
              plugin.getIds()[0],
              plugin.getName(),
              plugin.getDescription(),
              plugin.getClassMap().get(interfaceClass),
              plugin.getLibraries(),
              plugin.getImageFile()));
    }
    Collections.sort(list);
    return list;
  }
}
