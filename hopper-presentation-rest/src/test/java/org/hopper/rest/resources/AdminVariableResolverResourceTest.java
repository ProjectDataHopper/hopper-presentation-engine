package org.hopper.rest.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AdminVariableResolverResourceTest {

  @Test
  void parseBareSecretId() {
    var a = AdminVariableResolverResource.parseTestArgument("edw-db-password", "gsm");
    assertEquals("edw-db-password", a.secretPath);
    assertNull(a.jsonKey);
  }

  @Test
  void parseFullExpression() {
    var a = AdminVariableResolverResource.parseTestArgument("#{gsm:edw:db}", "gsm");
    assertEquals("edw", a.secretPath);
    assertEquals("db", a.jsonKey);
  }

  @Test
  void parseExpressionPathOnly() {
    var a = AdminVariableResolverResource.parseTestArgument("#{gsm:my-secret}", "gsm");
    assertEquals("my-secret", a.secretPath);
    assertNull(a.jsonKey);
  }

  @Test
  void doesNotTreatGsmResourceNameAsPathKey() {
    // Full resource names with slashes stay intact
    var a =
        AdminVariableResolverResource.parseTestArgument(
            "projects/apachehop/secrets/foo/versions/latest", "gsm");
    assertEquals("projects/apachehop/secrets/foo/versions/latest", a.secretPath);
    assertNull(a.jsonKey);
  }
}
