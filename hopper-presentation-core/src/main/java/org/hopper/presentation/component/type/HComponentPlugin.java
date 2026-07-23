package org.hopper.presentation.component.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HComponentPlugin {

  /** @return The ID of the component */
  String id();

  /** @return The name of the component */
  String name();

  /** @return The description of the component */
  String description() default "";

  /**
   * Classpath path to an SVG (or other image) resource in this plugin's JAR, e.g. {@code
   * ui/images/components/label.svg}. Empty means clients should use a default icon. Exposed via
   * {@code IPlugin#getImageFile()} and {@code GET plugins/components}.
   */
  String image() default "";
}
