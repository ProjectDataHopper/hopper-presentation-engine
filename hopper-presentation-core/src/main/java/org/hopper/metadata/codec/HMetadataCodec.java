package org.hopper.metadata.codec;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.core.HJson;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.json.simple.JSONObject;

/**
 * Parse and serialize presentation/connector metadata in both Jackson flat and Hop polymorphic
 * wire shapes.
 *
 * <p>Prefer {@link #parsePresentation(String)} / {@link #parseConnector(String)} for imports and
 * AI-authored JSON. Prefer {@link #toHopJson(HPresentation, IHopMetadataProvider)} for files that
 * must match the metadata catalog on disk.
 */
public final class HMetadataCodec {

  private HMetadataCodec() {}

  /**
   * Parse a presentation from JSON (Hop polymorphic or flat pluginId shape).
   *
   * <p>Uses Jackson + dual-format deserializers first; on failure tries Hop {@link
   * JsonMetadataParser}.
   */
  public static HPresentation parsePresentation(String json) throws HException {
    if (json == null || json.isBlank()) {
      throw new HException("Presentation JSON is empty");
    }
    try {
      return HJson.createMapper().readValue(json, HPresentation.class);
    } catch (Exception jacksonEx) {
      try {
        return parsePresentationHop(json, new MemoryMetadataProvider());
      } catch (Exception hopEx) {
        throw new HException(
            "Unable to parse presentation JSON: " + jacksonEx.getMessage(), jacksonEx);
      }
    }
  }

  /** Parse presentation using Hop metadata parser (canonical disk shape). */
  public static HPresentation parsePresentationHop(String json, IHopMetadataProvider provider)
      throws HException {
    if (provider == null) {
      provider = new MemoryMetadataProvider();
    }
    try {
      JsonMetadataParser<HPresentation> parser =
          new JsonMetadataParser<>(HPresentation.class, provider);
      return parser.loadJsonObject(
          HPresentation.class, new JsonFactory().createParser(json));
    } catch (Exception e) {
      throw new HException("Unable to parse presentation via Hop metadata: " + e.getMessage(), e);
    }
  }

  /** Parse a connector wrapper from JSON (either wire shape). */
  public static HConnector parseConnector(String json) throws HException {
    if (json == null || json.isBlank()) {
      throw new HException("Connector JSON is empty");
    }
    try {
      return HJson.createMapper().readValue(json, HConnector.class);
    } catch (Exception jacksonEx) {
      try {
        return parseConnectorHop(json, new MemoryMetadataProvider());
      } catch (Exception hopEx) {
        throw new HException(
            "Unable to parse connector JSON: " + jacksonEx.getMessage(), jacksonEx);
      }
    }
  }

  public static HConnector parseConnectorHop(String json, IHopMetadataProvider provider)
      throws HException {
    if (provider == null) {
      provider = new MemoryMetadataProvider();
    }
    try {
      JsonMetadataParser<HConnector> parser =
          new JsonMetadataParser<>(HConnector.class, provider);
      return parser.loadJsonObject(HConnector.class, new JsonFactory().createParser(json));
    } catch (Exception e) {
      throw new HException("Unable to parse connector via Hop metadata: " + e.getMessage(), e);
    }
  }

  /** Serialize with Jackson ({@link HJson}); typically flat {@code pluginId} on plugins. */
  public static String toJacksonJson(HPresentation presentation, boolean pretty)
      throws HException {
    try {
      if (presentation == null) {
        throw new HException("Presentation is null");
      }
      return presentation.toJsonString(pretty);
    } catch (JsonProcessingException e) {
      throw new HException("Unable to serialize presentation: " + e.getMessage(), e);
    }
  }

  public static String toJacksonJson(HConnector connector, boolean pretty) throws HException {
    try {
      if (connector == null) {
        throw new HException("Connector is null");
      }
      if (pretty) {
        return HJson.createMapper().writerWithDefaultPrettyPrinter().writeValueAsString(connector);
      }
      return HJson.createMapper().writeValueAsString(connector);
    } catch (JsonProcessingException e) {
      throw new HException("Unable to serialize connector: " + e.getMessage(), e);
    }
  }

  /**
   * Serialize to Hop catalog JSON (polymorphic plugin wrappers). Requires a metadata provider for
   * the parser (in-memory is fine).
   */
  public static String toHopJson(HPresentation presentation, IHopMetadataProvider provider)
      throws HException {
    if (presentation == null) {
      throw new HException("Presentation is null");
    }
    if (provider == null) {
      provider = new MemoryMetadataProvider();
    }
    try {
      JsonMetadataParser<HPresentation> parser =
          new JsonMetadataParser<>(HPresentation.class, provider);
      JSONObject json = parser.getJsonObject(presentation);
      return json.toJSONString();
    } catch (Exception e) {
      throw new HException("Unable to serialize presentation to Hop JSON: " + e.getMessage(), e);
    }
  }

  public static String toHopJson(HConnector connector, IHopMetadataProvider provider)
      throws HException {
    if (connector == null) {
      throw new HException("Connector is null");
    }
    if (provider == null) {
      provider = new MemoryMetadataProvider();
    }
    try {
      JsonMetadataParser<HConnector> parser =
          new JsonMetadataParser<>(HConnector.class, provider);
      JSONObject json = parser.getJsonObject(connector);
      return json.toJSONString();
    } catch (Exception e) {
      throw new HException("Unable to serialize connector to Hop JSON: " + e.getMessage(), e);
    }
  }
}
