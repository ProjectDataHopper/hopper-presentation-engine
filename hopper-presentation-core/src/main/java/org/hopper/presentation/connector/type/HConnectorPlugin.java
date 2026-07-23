package org.hopper.presentation.connector.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HConnectorPlugin {

  /** @return The ID of the connector */
  String id();

  /** @return The name of the connector */
  String name();

  /** @return The description of the connector */
  String description();

  /**
   * Classpath path to an SVG (or other image) resource in this plugin's JAR, e.g. {@code
   * ui/images/connectors/sql.svg}. Empty means clients should use a default icon. Exposed via {@code
   * IPlugin#getImageFile()} and {@code GET plugins/connectors}.
   */
  String image() default "";
}
