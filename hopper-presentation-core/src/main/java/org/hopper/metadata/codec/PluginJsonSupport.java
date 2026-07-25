package org.hopper.metadata.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginRegistry;

/**
 * Shared helpers for Hop polymorphic plugin JSON vs flat {@code pluginId} Jackson JSON.
 *
 * <p><b>Canonical file shape (Hop {@code JsonMetadataParser}):</b>
 *
 * <pre>{@code
 * "component": { "HLabelComponent": { "pluginId": "HLabelComponent", "label": "Hi" } }
 * }</pre>
 *
 * <p><b>Flat Jackson shape (also accepted):</b>
 *
 * <pre>{@code
 * "component": { "pluginId": "HLabelComponent", "label": "Hi" }
 * }</pre>
 */
public final class PluginJsonSupport {

  private PluginJsonSupport() {}

  /**
   * Resolve a plugin body from either flat or single-key polymorphic form, instantiate the class,
   * and bind properties.
   *
   * @param rootNode component/connector object node
   * @param pluginType Hop plugin type class
   * @param kind label for errors ({@code "component"} / {@code "connector"})
   * @param mapper Jackson mapper from the parse context
   */
  @SuppressWarnings("unchecked")
  public static <T> T deserializePlugin(
      ObjectNode rootNode,
      Class<? extends IPluginType> pluginType,
      String kind,
      ObjectMapper mapper)
      throws IOException {
    if (rootNode == null || rootNode.isNull()) {
      throw new IOException("Missing " + kind + " plugin object");
    }

    String pluginId = null;
    ObjectNode body = rootNode;

    if (rootNode.has("pluginId") && !rootNode.get("pluginId").isNull()) {
      String id = rootNode.get("pluginId").asText();
      if (StringUtils.isNotEmpty(id)) {
        pluginId = id;
        body = rootNode;
      }
    }

    if (pluginId == null) {
      // Hop polymorphic: single key = plugin id, value = property bag
      String onlyKey = null;
      ObjectNode onlyObject = null;
      int objectKeys = 0;
      Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> e = fields.next();
        if (e.getValue() != null && e.getValue().isObject()) {
          objectKeys++;
          onlyKey = e.getKey();
          onlyObject = (ObjectNode) e.getValue();
        }
      }
      if (objectKeys == 1 && onlyKey != null && onlyObject != null) {
        pluginId = onlyKey;
        body = onlyObject.deepCopy();
        if (!body.has("pluginId") || StringUtils.isEmpty(body.get("pluginId").asText())) {
          body.put("pluginId", pluginId);
        }
      }
    }

    if (StringUtils.isEmpty(pluginId)) {
      throw new IOException(
          "Unable to find "
              + kind
              + " plugin id (expected flat pluginId or single-key Hop wrapper) in "
              + rootNode);
    }

    PluginRegistry registry = PluginRegistry.getInstance();
    IPlugin plugin = registry.findPluginWithId(pluginType, pluginId);
    if (plugin == null) {
      throw new IOException("Unable to load " + kind + " plugin with ID: " + pluginId);
    }
    try {
      Object instance = registry.loadClass(plugin);
      if (instance == null) {
        throw new IOException(
            "Hopper " + kind + " plugin with id " + pluginId + " is not registered");
      }
      return (T) mapper.treeToValue(body, instance.getClass());
    } catch (HopPluginException e) {
      throw new IOException(
          "Unable to load " + kind + " plugin with ID " + pluginId + ": " + e, e);
    }
  }
}
