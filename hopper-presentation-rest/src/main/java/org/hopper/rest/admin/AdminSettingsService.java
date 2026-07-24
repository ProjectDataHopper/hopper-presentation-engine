package org.hopper.rest.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.audit.HAuditConfig;
import org.hopper.audit.HAuditEmitter;
import org.hopper.audit.HAuditEvent;
import org.hopper.audit.HAuditEventType;
import org.hopper.config.HEffectiveSetting;
import org.hopper.config.HServerSettings;
import org.hopper.config.HSettingDefinition;
import org.hopper.config.HSettingsCatalog;
import org.hopper.config.HSettingsMerger;
import org.hopper.rest.security.HSecuritySettings;
import org.hopper.security.HPrincipal;
import org.hopper.security.HSecurityContext;

/**
 * Loads/saves L1 runtime settings overrides and builds effective configuration for the admin API.
 */
public class AdminSettingsService {

  private final Properties bootstrapProperties;
  private final IHopMetadataProvider metadataProvider;
  private final AtomicReference<Map<String, String>> overridesRef =
      new AtomicReference<>(Map.of());

  public AdminSettingsService(Properties bootstrapProperties, IHopMetadataProvider metadataProvider) {
    this.bootstrapProperties = bootstrapProperties != null ? copyProps(bootstrapProperties) : new Properties();
    this.metadataProvider = metadataProvider;
  }

  /** Load overrides from metadata (call at startup). Missing document → empty overrides. */
  public void loadFromMetadata() {
    try {
      IHopMetadataSerializer<HServerSettings> serializer =
          metadataProvider.getSerializer(HServerSettings.class);
      if (serializer.exists(HServerSettings.DOCUMENT_NAME)) {
        HServerSettings doc = serializer.load(HServerSettings.DOCUMENT_NAME);
        if (doc != null) {
          overridesRef.set(Map.copyOf(doc.propertiesAsMap()));
          return;
        }
      }
    } catch (Exception ignored) {
      // Soft-fail: bootstrap-only config
    }
    overridesRef.set(Map.of());
  }

  public Map<String, String> getOverrides() {
    return overridesRef.get();
  }

  public Properties getBootstrapProperties() {
    return copyProps(bootstrapProperties);
  }

  /** Merged properties (defaults + bootstrap + overrides) for building HSecuritySettings etc. */
  public Properties effectiveProperties() {
    return HSettingsMerger.merge(bootstrapProperties, overridesRef.get());
  }

  public List<HEffectiveSetting> listEffective(boolean redactSecrets) {
    return HSettingsMerger.effectiveList(bootstrapProperties, overridesRef.get(), redactSecrets);
  }

  public List<Map<String, Object>> schema() {
    List<Map<String, Object>> list = new ArrayList<>();
    for (HSettingDefinition def : HSettingsCatalog.all()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("key", def.getKey());
      m.put("category", def.getCategory().name());
      m.put("type", def.getType().name());
      m.put("defaultValue", def.getDefaultValue());
      m.put("description", def.getDescription());
      m.put("restartRequired", def.isRestartRequired());
      m.put("sensitive", def.isSensitive() || def.getType().name().equals("SECRET_REF"));
      m.put("readOnly", def.isReadOnly());
      if (!def.getEnumValues().isEmpty()) {
        m.put("enumValues", def.getEnumValues());
      }
      if (def.getMin() != null) {
        m.put("min", def.getMin());
      }
      if (def.getMax() != null) {
        m.put("max", def.getMax());
      }
      list.add(m);
    }
    return list;
  }

  /**
   * Validate, persist overrides, update in-memory map. Does not rebuild runtime services — caller
   * ({@code HRest}) applies hot-reload.
   */
  public ApplyResult applyPatch(Map<String, String> patch) throws Exception {
    HSettingsMerger.ValidationResult validation = HSettingsMerger.validatePatch(patch);
    if (!validation.isValid()) {
      return ApplyResult.invalid(validation.errors(), validation.warnings());
    }

    Map<String, String> mergedOverrides =
        HSettingsMerger.applyPatchToOverrides(overridesRef.get(), patch);

    // Persist
    HServerSettings doc = HServerSettings.emptyRuntime();
    doc.setPropertiesFromMap(mergedOverrides);
    doc.setRevision(System.currentTimeMillis());
    doc.setUpdatedAt(Instant.now().toString());
    HPrincipal principal = HSecurityContext.getPrincipal();
    if (principal != null && !principal.isAnonymous()) {
      doc.setUpdatedBy(principal.getUsername());
    }
    IHopMetadataSerializer<HServerSettings> serializer =
        metadataProvider.getSerializer(HServerSettings.class);
    serializer.save(doc);

    overridesRef.set(Map.copyOf(mergedOverrides));

    List<String> applied = new ArrayList<>(patch.keySet());
    List<String> restartRequired = new ArrayList<>();
    for (String key : applied) {
      HSettingsCatalog.find(key)
          .filter(HSettingDefinition::isRestartRequired)
          .ifPresent(d -> restartRequired.add(key));
    }

    emitSecurityChange(applied);

    return ApplyResult.ok(applied, restartRequired, validation.warnings());
  }

  public HSecuritySettings buildSecuritySettings() {
    return HSecuritySettings.fromProperties(effectiveProperties());
  }

  public HAuditConfig buildAuditConfig() {
    return HAuditConfig.fromProperties(effectiveProperties());
  }

  private void emitSecurityChange(List<String> keys) {
    try {
      HPrincipal p = HSecurityContext.getPrincipal();
      HAuditEvent event = HAuditEvent.of(HAuditEventType.SECURITY_CHANGE);
      event.actor(p);
      event.actionCode("admin.settings.apply");
      event.getAttributes().put("keys", String.join(",", keys));
      HAuditEmitter.getInstance().emit(event);
    } catch (Exception ignored) {
      // fail-open
    }
  }

  private static Properties copyProps(Properties source) {
    Properties p = new Properties();
    if (source != null) {
      for (String name : source.stringPropertyNames()) {
        p.setProperty(name, source.getProperty(name));
      }
    }
    return p;
  }

  public record ApplyResult(
      boolean success,
      List<String> errors,
      List<String> warnings,
      List<String> applied,
      List<String> restartRequired) {

    public static ApplyResult invalid(List<String> errors, List<String> warnings) {
      return new ApplyResult(
          false,
          errors != null ? errors : List.of(),
          warnings != null ? warnings : List.of(),
          List.of(),
          List.of());
    }

    public static ApplyResult ok(
        List<String> applied, List<String> restartRequired, List<String> warnings) {
      return new ApplyResult(
          true,
          List.of(),
          warnings != null ? warnings : List.of(),
          applied != null ? applied : List.of(),
          restartRequired != null ? restartRequired : List.of());
    }

    public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("success", success);
      m.put("errors", errors);
      m.put("warnings", warnings);
      m.put("applied", applied);
      m.put("restartRequired", restartRequired);
      return m;
    }
  }
}
