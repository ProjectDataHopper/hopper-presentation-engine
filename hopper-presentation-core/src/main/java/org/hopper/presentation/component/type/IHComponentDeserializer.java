package org.hopper.presentation.component.type;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;

import java.io.IOException;

public class IHComponentDeserializer extends JsonDeserializer<IHComponent> {

  @Override
  public IHComponent deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
    ObjectNode rootNode = mapper.readTree(jsonParser);

    // Get the ID of the component plugin...
    //
    if (rootNode.has("pluginId")) {
      String id = rootNode.get("pluginId").asText();
      if (!StringUtils.isEmpty(id)) {
        // Load the component Plugin class
        //
        PluginRegistry pluginRegistry = PluginRegistry.getInstance();
        IPlugin plugin = pluginRegistry.findPluginWithId(HComponentPluginType.class, id);
        if (plugin == null) {
          throw new IOException("Unable to load component plugin with ID : " + id);
        }
        try {
          // Load/Create empty class of the right class
          //
          IHComponent component = (IHComponent) pluginRegistry.loadClass(plugin);
          if (component == null) {
            throw new IOException("Hopper component plugin with id " + id + " is not registered");
          }

          // Now do the proper de-serialization of this class
          //
            return mapper.readValue(rootNode.toString(), component.getClass());
        } catch (HopPluginException e) {
          throw new IOException(
              "Unable to load component plugin with ID " + id + " : " + e.toString(), e);
        }
      }
    }
    throw new IOException("Unable to find ID of component in " + rootNode.toString());
  }
}
