package org.hopper.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Merges defaults → bootstrap (L0) → overrides (L1) into effective settings and validates patches.
 *
 * <p>Secret resolution (env expansion) is intentionally left to consumers such as {@code
 * HSecuritySettings}; this class only merges string layers and redacts for API display.
 */
public final class HSettingsMerger {

  public static final String REDACTED = "***";

  private HSettingsMerger() {}

  /**
   * Build effective properties: catalog defaults, then bootstrap props, then runtime overrides.
   * Unknown bootstrap keys are preserved so legacy properties still flow to consumers.
   */
  public static Properties merge(Properties bootstrap, Map<String, String> overrides) {
    Properties result = new Properties();

    // Defaults from catalog (only non-empty defaults)
    for (Map.Entry<String, String> e : HSettingsCatalog.defaultProperties().entrySet()) {
      result.setProperty(e.getKey(), e.getValue());
    }

    if (bootstrap != null) {
      for (String name : bootstrap.stringPropertyNames()) {
        String v = bootstrap.getProperty(name);
        if (v != null) {
          result.setProperty(name, v);
        }
      }
    }

    if (overrides != null) {
      for (Map.Entry<String, String> e : overrides.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) {
          continue;
        }
        result.setProperty(e.getKey().trim(), e.getValue() != null ? e.getValue() : "");
      }
    }
    return result;
  }

  /** Resolve source for a known key after merge. */
  public static HSettingSource sourceOf(
      String key, Properties bootstrap, Map<String, String> overrides) {
    if (overrides != null && overrides.containsKey(key)) {
      return HSettingSource.OVERRIDE;
    }
    if (bootstrap != null && bootstrap.getProperty(key) != null) {
      String raw = bootstrap.getProperty(key);
      if (isEnvRef(raw)) {
        return HSettingSource.ENV;
      }
      return HSettingSource.BOOTSTRAP;
    }
    return HSettingSource.DEFAULT;
  }

  /**
   * Build API-safe effective settings for all known catalog keys (plus optional extra keys present
   * only in bootstrap/overrides).
   */
  public static List<HEffectiveSetting> effectiveList(
      Properties bootstrap, Map<String, String> overrides, boolean redactSecrets) {
    Properties merged = merge(bootstrap, overrides);
    List<HEffectiveSetting> list = new ArrayList<>();

    for (HSettingDefinition def : HSettingsCatalog.all()) {
      list.add(toEffective(def, merged, bootstrap, overrides, redactSecrets));
    }

    // Extra keys not in catalog (legacy)
    for (String name : merged.stringPropertyNames()) {
      if (HSettingsCatalog.isKnown(name)) {
        continue;
      }
      String value = merged.getProperty(name, "");
      HSettingSource src = sourceOf(name, bootstrap, overrides);
      list.add(
          new HEffectiveSetting(
              name,
              HSettingCategory.SERVER,
              HSettingType.STRING,
              value,
              src,
              false,
              false,
              false,
              value != null && !value.isBlank(),
              "Unregistered property (from bootstrap or override).",
              ""));
    }
    return list;
  }

  public static HEffectiveSetting toEffective(
      HSettingDefinition def,
      Properties merged,
      Properties bootstrap,
      Map<String, String> overrides,
      boolean redactSecrets) {
    String key = def.getKey();
    String raw = merged != null ? merged.getProperty(key, def.getDefaultValue()) : def.getDefaultValue();
    if (raw == null) {
      raw = "";
    }
    HSettingSource source = sourceOf(key, bootstrap, overrides);
    boolean configured = !raw.isBlank();
    String display = raw;
    if (redactSecrets && shouldRedact(def, raw)) {
      display = REDACTED;
    }
    return new HEffectiveSetting(
        key,
        def.getCategory(),
        def.getType(),
        display,
        source,
        def.isSensitive() || def.getType() == HSettingType.SECRET_REF,
        def.isReadOnly(),
        def.isRestartRequired(),
        configured,
        def.getDescription(),
        def.getDefaultValue());
  }

  /**
   * Redact non-reference secret values. Env refs like {@code ${FOO}} are shown as-is so admins can
   * see which variable is wired.
   */
  public static boolean shouldRedact(HSettingDefinition def, String raw) {
    if (def == null) {
      return false;
    }
    if (!(def.isSensitive() || def.getType() == HSettingType.SECRET_REF)) {
      return false;
    }
    if (raw == null || raw.isBlank()) {
      return false;
    }
    return !isEnvRef(raw);
  }

  public static boolean isEnvRef(String value) {
    if (value == null) {
      return false;
    }
    String v = value.trim();
    return v.startsWith("${") && v.endsWith("}") && v.length() > 3;
  }

  /**
   * Validate a patch map against the catalog. Returns error messages (empty = valid).
   * Unknown keys are allowed but noted as warnings via {@link ValidationResult#warnings()}.
   */
  public static ValidationResult validatePatch(Map<String, String> patch) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (patch == null || patch.isEmpty()) {
      errors.add("Patch is empty");
      return new ValidationResult(errors, warnings);
    }
    for (Map.Entry<String, String> e : patch.entrySet()) {
      String key = e.getKey() != null ? e.getKey().trim() : "";
      if (key.isEmpty()) {
        errors.add("Empty setting key");
        continue;
      }
      var defOpt = HSettingsCatalog.find(key);
      if (defOpt.isEmpty()) {
        warnings.add("Unknown setting key will be stored as override: " + key);
        continue;
      }
      HSettingDefinition def = defOpt.get();
      if (def.isReadOnly()) {
        errors.add("Setting is read-only: " + key);
        continue;
      }
      String value = e.getValue() != null ? e.getValue() : "";
      validateValue(def, value, errors);
    }
    return new ValidationResult(errors, warnings);
  }

  private static void validateValue(HSettingDefinition def, String value, List<String> errors) {
    Objects.requireNonNull(def);
    switch (def.getType()) {
      case BOOLEAN -> {
        if (!value.isBlank()
            && !isBoolean(value)) {
          errors.add(def.getKey() + ": expected boolean, got '" + value + "'");
        }
      }
      case INT -> {
        if (value.isBlank()) {
          return;
        }
        try {
          int n = Integer.parseInt(value.trim());
          if (def.getMin() != null && n < def.getMin()) {
            errors.add(def.getKey() + ": minimum is " + def.getMin());
          }
          if (def.getMax() != null && n > def.getMax()) {
            errors.add(def.getKey() + ": maximum is " + def.getMax());
          }
        } catch (NumberFormatException ex) {
          errors.add(def.getKey() + ": expected integer, got '" + value + "'");
        }
      }
      case ENUM -> {
        if (value.isBlank()) {
          return;
        }
        boolean ok = false;
        for (String allowed : def.getEnumValues()) {
          if (allowed.equalsIgnoreCase(value.trim())) {
            ok = true;
            break;
          }
        }
        if (!ok) {
          errors.add(
              def.getKey()
                  + ": expected one of "
                  + def.getEnumValues()
                  + ", got '"
                  + value
                  + "'");
        }
      }
      case SECRET_REF -> {
        // Prefer env refs; raw secrets are allowed but discouraged (no hard error)
      }
      default -> {
        // string / string-list freeform
      }
    }
  }

  private static boolean isBoolean(String value) {
    String v = value.trim().toLowerCase(Locale.ROOT);
    return "true".equals(v)
        || "false".equals(v)
        || "y".equals(v)
        || "n".equals(v)
        || "yes".equals(v)
        || "no".equals(v)
        || "1".equals(v)
        || "0".equals(v);
  }

  /**
   * Merge an existing override map with a patch (patch wins). Keys with {@code null} value in the
   * patch remove the override (fall back to bootstrap/default).
   */
  public static Map<String, String> applyPatchToOverrides(
      Map<String, String> existing, Map<String, String> patch) {
    Map<String, String> result = new LinkedHashMap<>();
    if (existing != null) {
      result.putAll(existing);
    }
    if (patch != null) {
      for (Map.Entry<String, String> e : patch.entrySet()) {
        if (e.getKey() == null || e.getKey().isBlank()) {
          continue;
        }
        String key = e.getKey().trim();
        if (e.getValue() == null) {
          result.remove(key);
        } else {
          result.put(key, e.getValue());
        }
      }
    }
    return result;
  }

  public record ValidationResult(List<String> errors, List<String> warnings) {
    public boolean isValid() {
      return errors == null || errors.isEmpty();
    }
  }
}
