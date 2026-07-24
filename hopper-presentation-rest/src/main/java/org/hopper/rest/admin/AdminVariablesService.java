package org.hopper.rest.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEventType;
import org.hopper.audit.lineage.HUsageAudit;
import org.hopper.config.HSystemVariables;
import org.hopper.config.HVariableEntry;
import org.hopper.security.HPrincipal;
import org.hopper.security.HSecurityContext;

/**
 * Loads/saves system variables metadata and applies them to the shared server variable space.
 */
public class AdminVariablesService {

  private final IHopMetadataProvider metadataProvider;
  private final AtomicReference<Map<String, String>> variablesRef =
      new AtomicReference<>(Map.of());
  /** Names last applied so deletes can clear variables from the live space. */
  private final AtomicReference<Set<String>> appliedNamesRef =
      new AtomicReference<>(Set.of());

  public AdminVariablesService(IHopMetadataProvider metadataProvider) {
    this.metadataProvider = metadataProvider;
  }

  /**
   * Load from metadata (call at startup). Missing document → empty set and create an empty {@code
   * system-variables/runtime} document so the folder/file exist for later saves.
   */
  public void loadFromMetadata() {
    try {
      IHopMetadataSerializer<HSystemVariables> serializer =
          metadataProvider.getSerializer(HSystemVariables.class);
      if (serializer.exists(HSystemVariables.DOCUMENT_NAME)) {
        HSystemVariables doc = serializer.load(HSystemVariables.DOCUMENT_NAME);
        if (doc != null) {
          variablesRef.set(Map.copyOf(doc.variablesAsMap()));
          return;
        }
      }
      // First run: persist an empty runtime document so metadata.path/system-variables/ exists
      ensureRuntimeDocument(serializer);
    } catch (Exception ignored) {
      // Soft-fail: no system variables
    }
    variablesRef.set(Map.of());
  }

  /**
   * Ensure the singleton runtime document exists on disk (empty variable list). Safe to call
   * repeatedly. Returns the in-memory map after load/create.
   */
  public Map<String, String> ensureLoaded() {
    try {
      IHopMetadataSerializer<HSystemVariables> serializer =
          metadataProvider.getSerializer(HSystemVariables.class);
      if (serializer.exists(HSystemVariables.DOCUMENT_NAME)) {
        HSystemVariables doc = serializer.load(HSystemVariables.DOCUMENT_NAME);
        if (doc != null) {
          Map<String, String> map = Map.copyOf(doc.variablesAsMap());
          variablesRef.set(map);
          return map;
        }
      }
      ensureRuntimeDocument(serializer);
    } catch (Exception ignored) {
      // keep current in-memory state
    }
    return variablesRef.get();
  }

  private void ensureRuntimeDocument(IHopMetadataSerializer<HSystemVariables> serializer)
      throws Exception {
    HSystemVariables empty = HSystemVariables.emptyRuntime();
    empty.setUpdatedAt(Instant.now().toString());
    empty.setRevision(0L);
    serializer.save(empty);
  }

  public Map<String, String> getVariables() {
    return variablesRef.get();
  }

  public List<Map<String, String>> listEntries(boolean redactEncrypted) {
    List<Map<String, String>> list = new ArrayList<>();
    for (Map.Entry<String, String> e : variablesRef.get().entrySet()) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("name", e.getKey());
      String value = e.getValue() != null ? e.getValue() : "";
      if (redactEncrypted && looksEncrypted(value)) {
        row.put("value", "********");
        row.put("redacted", "true");
      } else {
        row.put("value", value);
      }
      list.add(row);
    }
    return list;
  }

  /**
   * Replace the system variable set, persist, and apply to {@code liveVariables}.
   *
   * @param entries name/value pairs (empty names skipped)
   * @param liveVariables server-wide variable space (may be null to skip apply)
   * @return saved entry count
   */
  public int saveAndApply(List<HVariableEntry> entries, IVariables liveVariables) throws Exception {
    Map<String, String> next = new LinkedHashMap<>();
    if (entries != null) {
      for (HVariableEntry e : entries) {
        if (e == null || e.getName() == null || e.getName().isBlank()) {
          continue;
        }
        next.put(e.getName().trim(), e.getValue() != null ? e.getValue() : "");
      }
    }

    HSystemVariables doc = HSystemVariables.emptyRuntime();
    doc.setVariablesFromMap(next);
    doc.setUpdatedAt(Instant.now().toString());
    HPrincipal principal = HSecurityContext.getPrincipal();
    if (principal != null && principal.getUsername() != null) {
      doc.setUpdatedBy(principal.getUsername());
    }
    long revision = 1L;
    try {
      IHopMetadataSerializer<HSystemVariables> serializer =
          metadataProvider.getSerializer(HSystemVariables.class);
      if (serializer.exists(HSystemVariables.DOCUMENT_NAME)) {
        HSystemVariables existing = serializer.load(HSystemVariables.DOCUMENT_NAME);
        if (existing != null) {
          revision = existing.getRevision() + 1;
        }
      }
      doc.setRevision(revision);
      serializer.save(doc);
    } catch (Exception e) {
      throw e;
    }

    variablesRef.set(Map.copyOf(next));
    applyTo(liveVariables);

    HAuditEmitter.getInstance()
        .emitSafely(
            HUsageAudit.metadataChange(
                HAuditEventType.METADATA_UPDATE,
                "system-variables",
                HSystemVariables.DOCUMENT_NAME,
                principal));

    return next.size();
  }

  /** Apply current system variables onto the live space (remove keys that were deleted). */
  public void applyTo(IVariables liveVariables) {
    if (liveVariables == null) {
      return;
    }
    Map<String, String> current = variablesRef.get();
    Set<String> previous = appliedNamesRef.get();
    for (String name : previous) {
      if (!current.containsKey(name)) {
        liveVariables.setVariable(name, null);
      }
    }
    for (Map.Entry<String, String> e : current.entrySet()) {
      liveVariables.setVariable(e.getKey(), e.getValue());
    }
    appliedNamesRef.set(Set.copyOf(new LinkedHashSet<>(current.keySet())));
  }

  /** Hop-compatible encrypt; leaves values that already use variables/resolvers alone. */
  public static String encryptIfNotUsingVariables(String value) {
    return Encr.encryptPasswordIfNotUsingVariables(value);
  }

  static boolean looksEncrypted(String value) {
    if (value == null) {
      return false;
    }
    String v = value.trim();
    return v.startsWith(Encr.PASSWORD_ENCRYPTED_PREFIX) || v.startsWith("AES2 ");
  }
}
