package org.hopper.rest.history;

import com.fasterxml.jackson.core.JsonFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataParser;
import org.hopper.core.exception.HException;
import org.hopper.presentation.HPresentation;

/** Serialize / restore full presentation JSON for undo snapshots. */
public final class PresentationSnapshot {

  private PresentationSnapshot() {}

  public static String toJson(HPresentation presentation, IHopMetadataProvider provider)
      throws HException {
    if (presentation == null) {
      throw new HException("Cannot snapshot a null presentation");
    }
    try {
      JsonMetadataParser<HPresentation> parser =
          new JsonMetadataParser<>(HPresentation.class, provider);
      return parser.getJsonObject(presentation).toJSONString();
    } catch (Exception e) {
      throw new HException(
          "Error serializing presentation '" + presentation.getName() + "' for undo", e);
    }
  }

  public static HPresentation fromJson(String json, IHopMetadataProvider provider)
      throws HException {
    if (json == null || json.isBlank()) {
      throw new HException("Cannot restore presentation from empty JSON");
    }
    try {
      JsonMetadataParser<HPresentation> parser =
          new JsonMetadataParser<>(HPresentation.class, provider);
      HPresentation presentation =
          parser.loadJsonObject(HPresentation.class, new JsonFactory().createParser(json));
      if (presentation == null) {
        throw new HException("Parsed presentation was null");
      }
      return presentation;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error restoring presentation from undo snapshot", e);
    }
  }

  public static String loadJson(String name, IHopMetadataProvider provider) throws HException {
    try {
      IHopMetadataSerializer<HPresentation> serializer =
          provider.getSerializer(HPresentation.class);
      HPresentation presentation = serializer.load(name);
      if (presentation == null) {
        throw new HException("Presentation not found: " + name);
      }
      return toJson(presentation, provider);
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error loading presentation '" + name + "' for undo", e);
    }
  }

  public static void saveJson(String json, IHopMetadataProvider provider) throws HException {
    try {
      HPresentation presentation = fromJson(json, provider);
      IHopMetadataSerializer<HPresentation> serializer =
          provider.getSerializer(HPresentation.class);
      serializer.save(presentation);
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error saving restored presentation", e);
    }
  }
}
