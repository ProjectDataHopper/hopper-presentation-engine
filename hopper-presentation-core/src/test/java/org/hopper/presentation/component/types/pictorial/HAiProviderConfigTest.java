package org.hopper.presentation.component.types.pictorial;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.presentation.datacontext.HGlobalVariables;

class HAiProviderConfigTest {

  @BeforeEach
  void setUp() throws Exception {
    HEnvironment.init();
    HGlobalVariables.clear();
  }

  @AfterEach
  void tearDown() {
    HGlobalVariables.clear();
  }

  @Test
  void variableExpressionIsNotEncryptedOnSave() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setRawApiKey("${GOOGLE_AI_API_KEY}");
    assertEquals("${GOOGLE_AI_API_KEY}", config.getEncryptedApiKey());
    assertTrue(config.isApiKeyVariableExpression());
    assertEquals("${GOOGLE_AI_API_KEY}", config.getMaskedApiKey());
  }

  @Test
  void resolverExpressionIsNotEncryptedOnSave() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setRawApiKey("#{gsm:hopper-ai-api-key}");
    assertEquals("#{gsm:hopper-ai-api-key}", config.getEncryptedApiKey());
    assertTrue(config.isApiKeyVariableExpression());
    assertEquals("#{gsm:hopper-ai-api-key}", config.getMaskedApiKey());
  }

  @Test
  void plainSecretIsObfuscated() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setRawApiKey("sk-plain-secret-value");
    assertTrue(config.getEncryptedApiKey().startsWith("Encrypted "));
    assertFalse(config.isApiKeyVariableExpression());
    assertTrue(config.getMaskedApiKey().contains("••••") || config.getMaskedApiKey().startsWith("Encrypted"));
  }

  @Test
  void decryptedApiKeyResolvesEnvStyleVariables() {
    Variables vars = new Variables();
    vars.setVariable("GOOGLE_AI_API_KEY", "resolved-secret-123");
    HGlobalVariables.set(vars);

    HAiProviderConfig config = new HAiProviderConfig();
    config.setRawApiKey("${GOOGLE_AI_API_KEY}");
    assertEquals("resolved-secret-123", config.getDecryptedApiKey());
    assertEquals("resolved-secret-123", config.getDecryptedApiKey(vars));
  }

  @Test
  void plainSecretDecryptsRoundTrip() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setRawApiKey("my-secret-key");
    String decrypted = config.getDecryptedApiKey();
    assertEquals("my-secret-key", decrypted);
  }

  @Test
  void testLiveConnectionBuiltinOk() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setProviderType(HAiProviderConfig.ProviderType.BUILTIN);
    var r = config.testLiveConnection();
    assertTrue(r.isOk());
    assertTrue(r.getMessage().toLowerCase().contains("built-in"));
  }

  @Test
  void testLiveConnectionMissingKeyFails() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setProviderType(HAiProviderConfig.ProviderType.XAI_GROK);
    config.setEncryptedApiKey("");
    var r = config.testLiveConnection();
    assertFalse(r.isOk());
    assertTrue(r.getMessage().toLowerCase().contains("empty") || r.getMessage().toLowerCase().contains("missing") || r.getMessage().toLowerCase().contains("api key"));
  }

  @Test
  void testLiveConnectionUnresolvedExpressionFails() {
    HAiProviderConfig config = new HAiProviderConfig();
    config.setProviderType(HAiProviderConfig.ProviderType.XAI_GROK);
    config.setRawApiKey("#{gsm:missing-secret}");
    // No resolver metadata in unit test → expression stays unresolved
    var r = config.testLiveConnection(new Variables());
    assertFalse(r.isOk());
    assertTrue(
        r.getMessage().contains("#{") || r.getMessage().toLowerCase().contains("unresolved"),
        r.getMessage());
  }
}
