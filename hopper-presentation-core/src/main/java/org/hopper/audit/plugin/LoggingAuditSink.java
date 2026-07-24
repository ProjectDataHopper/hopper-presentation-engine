package org.hopper.audit.plugin;

import java.util.Map;
import java.util.logging.Logger;
import org.apache.hop.core.variables.IVariables;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventJson;
import org.hopper.core.exception.HException;

/**
 * Built-in sink that writes audit events to a JDK logger (avoids Hop LogStore coupling).
 *
 * <p>Property {@code format}= {@code summary} (default) or {@code json}.
 */
@HAuditPlugin(
    id = "LoggingAuditSink",
    name = "Logging audit sink",
    description = "Writes audit event summaries or JSON to the application log")
public class LoggingAuditSink extends HBaseAuditSink implements IAuditSink {

  private static final Logger LOG = Logger.getLogger("org.hopper.audit");

  private boolean jsonFormat;

  @Override
  public void init(Map<String, String> properties, IVariables variables) throws HException {
    super.init(properties, variables);
    jsonFormat = "json".equalsIgnoreCase(property("format", "summary"));
  }

  @Override
  public void emit(HAuditEvent event) throws HException {
    if (event == null) {
      return;
    }
    if (jsonFormat) {
      LOG.info(HAuditEventJson.toJsonQuietly(event));
      return;
    }
    LOG.info(
        "AUDIT"
            + " type="
            + event.getEventType()
            + " outcome="
            + event.getOutcome()
            + " user="
            + event.getActorUsername()
            + " action="
            + event.getAction()
            + " resource="
            + event.getResourceType()
            + ":"
            + event.getResourceName()
            + " requestId="
            + event.getRequestId()
            + " durationMs="
            + event.getDurationMs()
            + " eventId="
            + event.getEventId());
  }
}
