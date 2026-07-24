package org.hopper.core.gui.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.hop.core.variables.resolver.GooleSecretManagerVariableResolver;
import org.junit.jupiter.api.Test;

/**
 * Rest module has hop-tech-google on the classpath, so classpath multi-jar scan should resolve
 * Google Secret Manager labels even when hop-core owns the same messages package path.
 */
class HGuiWidgetAdapterResolverI18nTest {

  @Test
  void resolvesGoogleSecretManagerProjectIdLabel() {
    String raw =
        "i18n:org.apache.hop.core.variables.resolver:GooleSecretManagerVariableResolver.label.ProjectId";
    String resolved =
        HGuiWidgetAdapter.resolveI18n(
            raw,
            "org.apache.hop.core.variables.resolver",
            GooleSecretManagerVariableResolver.class);
    assertFalse(resolved.startsWith("i18n:"), resolved);
    assertFalse(resolved.startsWith("!"), resolved);
    assertEquals("Project ID", resolved);
  }

  @Test
  void resolvesGoogleSecretManagerLocationIdLabel() {
    String raw =
        "i18n:org.apache.hop.core.variables.resolver:GooleSecretManagerVariableResolver.label.LocationId";
    String resolved =
        HGuiWidgetAdapter.resolveI18n(
            raw,
            "org.apache.hop.core.variables.resolver",
            GooleSecretManagerVariableResolver.class);
    assertEquals("Location ID (optional)", resolved);
  }
}
