package org.hopper.audit.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an {@link IAuditSink} implementation for Hop plugin discovery. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HAuditPlugin {

  /** Stable plugin id (e.g. {@code LoggingAuditSink}). */
  String id();

  String name();

  String description();
}
