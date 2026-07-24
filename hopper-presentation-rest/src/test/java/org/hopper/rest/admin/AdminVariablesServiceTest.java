package org.hopper.rest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.hopper.config.HVariableEntry;

class AdminVariablesServiceTest {

  @BeforeAll
  static void initHop() throws Exception {
    if (!HopClientEnvironment.isInitialized()) {
      HopClientEnvironment.init();
    }
  }

  @Test
  void applyToSetsAndRemovesVariables() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    AdminVariablesService service = new AdminVariablesService(provider);
    IVariables live = new Variables();

    service.saveAndApply(
        List.of(new HVariableEntry("A", "1"), new HVariableEntry("B", "2")), live);
    assertEquals("1", live.getVariable("A"));
    assertEquals("2", live.getVariable("B"));

    service.saveAndApply(List.of(new HVariableEntry("A", "1x")), live);
    assertEquals("1x", live.getVariable("A"));
    assertEquals(null, live.getVariable("B"));
  }

  @Test
  void encryptIfNotUsingVariables() {
    String enc = AdminVariablesService.encryptIfNotUsingVariables("secret");
    assertTrue(enc.startsWith(Encr.PASSWORD_ENCRYPTED_PREFIX));

    // Hop leaves values that contain ${…} (or %%…%%) unencrypted
    String withVar = AdminVariablesService.encryptIfNotUsingVariables(" ${MY_SECRET} ");
    assertFalse(withVar.startsWith(Encr.PASSWORD_ENCRYPTED_PREFIX));
    assertEquals(" ${MY_SECRET} ", withVar);
  }

  @Test
  void loadFromMetadataRoundTrip() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    AdminVariablesService writer = new AdminVariablesService(provider);
    IVariables live = new Variables();
    writer.saveAndApply(List.of(new HVariableEntry("FROM_META", "yes")), live);

    AdminVariablesService reader = new AdminVariablesService(provider);
    reader.loadFromMetadata();
    assertEquals("yes", reader.getVariables().get("FROM_META"));
  }

  @Test
  void ensureLoadedCreatesEmptyDocumentWhenMissing() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    AdminVariablesService service = new AdminVariablesService(provider);
    assertTrue(service.ensureLoaded().isEmpty());
    assertTrue(
        provider.getSerializer(org.hopper.config.HSystemVariables.class)
            .exists(org.hopper.config.HSystemVariables.DOCUMENT_NAME));
  }
}
