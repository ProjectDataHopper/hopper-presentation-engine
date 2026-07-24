package org.hopper.audit.plugin;

import java.util.Map;
import org.apache.hop.core.variables.IVariables;
import org.hopper.audit.HAuditEvent;
import org.hopper.core.exception.HException;

/**
 * Pluggable destination for usage / security audit events (log, file, Kafka, OpenSearch, …).
 *
 * <p>Implementations must be registered with {@link HAuditPlugin} and discovered via {@link
 * HAuditPluginType}.
 */
public interface IAuditSink {

  void init(Map<String, String> properties, IVariables variables) throws HException;

  void emit(HAuditEvent event) throws HException;

  default void flush() throws HException {}

  default void close() throws HException {}

  /** Return false to skip events this sink does not care about. */
  default boolean accepts(HAuditEvent event) {
    return true;
  }
}
