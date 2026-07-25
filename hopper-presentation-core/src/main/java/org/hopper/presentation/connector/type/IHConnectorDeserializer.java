package org.hopper.presentation.connector.type;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.hopper.metadata.codec.PluginJsonSupport;

/**
 * Deserializes {@link IHConnector} from flat {@code pluginId} JSON or Hop single-key polymorphic
 * wrapper JSON.
 */
public class IHConnectorDeserializer extends JsonDeserializer<IHConnector> {

  @Override
  public IHConnector deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
    ObjectNode rootNode = mapper.readTree(jsonParser);
    return PluginJsonSupport.deserializePlugin(
        rootNode, HConnectorPluginType.class, "connector", mapper);
  }
}
