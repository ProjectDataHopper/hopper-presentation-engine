package org.hopper.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.hop.metadata.api.HopMetadataBase;

/**
 * Shared Jackson configuration for Hopper presentation JSON. Hides Hop metadata runtime fields that
 * should not round-trip through presentation JSON.
 */
public final class HJson {

  private HJson() {}

  public static ObjectMapper createMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.addMixIn(HopMetadataBase.class, HopMetadataBaseMixin.class);
    return mapper;
  }

  /** Prevents computed/runtime Hop metadata fields from polluting presentation JSON. */
  abstract static class HopMetadataBaseMixin {
    @JsonIgnore
    abstract String getFullName();

    @JsonIgnore
    abstract String getMetadataProviderName();
  }
}
