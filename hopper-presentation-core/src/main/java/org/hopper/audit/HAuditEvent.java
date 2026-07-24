package org.hopper.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hopper.security.HPrincipal;
import org.hopper.security.HResourceRef;

/**
 * Structured usage / security audit event (schema version 1).
 *
 * <p>Mutable builder-style bean for easy assembly during request handling; freeze by not mutating
 * after {@link HAuditEmitter#emit}.
 */
@Getter
@Setter
public class HAuditEvent {

  public static final int SCHEMA_VERSION = 1;

  private int schemaVersion = SCHEMA_VERSION;
  private String eventId = UUID.randomUUID().toString();
  private HAuditEventType eventType;
  private Instant timestamp = Instant.now();
  private Long durationMs;
  private HAuditOutcome outcome = HAuditOutcome.SUCCESS;
  private String errorMessage;

  private String requestId;
  private String renderId;
  private String sessionId;
  private String parentEventId;

  private String actorSubject;
  private String actorUsername;
  private String actorEmail;
  private List<String> actorRoles = new ArrayList<>();
  private String authMethod;
  private String clientIp;
  private String userAgent;

  private String action;
  private String resourceType;
  private String resourceName;

  /** Design-time lineage snapshot (presentation, parameters, component/connector names). */
  private Map<String, Object> design = new LinkedHashMap<>();

  /** Execution lineage (connector runs, timings, row counts). */
  private Map<String, Object> execution = new LinkedHashMap<>();

  private String application = "hopper-presentation";
  private String applicationVersion;
  private String nodeId;

  private Map<String, Object> attributes = new LinkedHashMap<>();

  public static HAuditEvent of(HAuditEventType type) {
    HAuditEvent event = new HAuditEvent();
    event.setEventType(type);
    return event;
  }

  public HAuditEvent actor(HPrincipal principal) {
    if (principal != null) {
      this.actorSubject = principal.getSubject();
      this.actorUsername = principal.getUsername();
      this.actorEmail = principal.getEmail();
      this.actorRoles = new ArrayList<>(principal.getRoles());
      this.authMethod = principal.getAuthMethod();
    }
    return this;
  }

  public HAuditEvent resource(HResourceRef ref) {
    if (ref != null) {
      this.resourceType = ref.getType() != null ? ref.getType().name() : null;
      this.resourceName = ref.getName();
    }
    return this;
  }

  public HAuditEvent actionCode(String actionCode) {
    this.action = actionCode;
    return this;
  }
}
