package org.hopper.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hopper.core.HJson;

/** Serializes {@link HAuditEvent} to canonical schema-version-1 JSON maps / strings. */
public final class HAuditEventJson {

  private static final ObjectMapper MAPPER =
      HJson.createMapper().copy().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  private HAuditEventJson() {}

  public static Map<String, Object> toMap(HAuditEvent event) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (event == null) {
      return map;
    }
    map.put("schemaVersion", event.getSchemaVersion());
    map.put("eventId", event.getEventId());
    map.put("eventType", event.getEventType() != null ? event.getEventType().name() : null);
    map.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : null);
    map.put("durationMs", event.getDurationMs());
    map.put("outcome", event.getOutcome() != null ? event.getOutcome().name() : null);
    map.put("errorMessage", event.getErrorMessage());

    Map<String, Object> correlation = new LinkedHashMap<>();
    correlation.put("requestId", event.getRequestId());
    correlation.put("renderId", event.getRenderId());
    correlation.put("sessionId", event.getSessionId());
    correlation.put("parentEventId", event.getParentEventId());
    map.put("correlation", correlation);

    Map<String, Object> actor = new LinkedHashMap<>();
    actor.put("subject", event.getActorSubject());
    actor.put("username", event.getActorUsername());
    actor.put("email", event.getActorEmail());
    actor.put("roles", event.getActorRoles());
    actor.put("authMethod", event.getAuthMethod());
    actor.put("clientIp", event.getClientIp());
    actor.put("userAgent", event.getUserAgent());
    map.put("actor", actor);

    map.put("action", event.getAction());
    Map<String, Object> resource = new LinkedHashMap<>();
    resource.put("type", event.getResourceType());
    resource.put("name", event.getResourceName());
    map.put("resource", resource);

    map.put("design", event.getDesign() != null ? event.getDesign() : Map.of());
    map.put("execution", event.getExecution() != null ? event.getExecution() : Map.of());

    Map<String, Object> host = new LinkedHashMap<>();
    host.put("application", event.getApplication());
    host.put("version", event.getApplicationVersion());
    host.put("nodeId", event.getNodeId());
    map.put("host", host);

    if (event.getAttributes() != null && !event.getAttributes().isEmpty()) {
      map.put("attributes", event.getAttributes());
    }
    return map;
  }

  public static String toJson(HAuditEvent event) throws JsonProcessingException {
    return MAPPER.writeValueAsString(toMap(event));
  }

  public static String toJsonQuietly(HAuditEvent event) {
    try {
      return toJson(event);
    } catch (JsonProcessingException e) {
      return "{\"eventId\":\""
          + (event != null ? event.getEventId() : "")
          + "\",\"error\":\"json-serialize-failed\"}";
    }
  }
}
