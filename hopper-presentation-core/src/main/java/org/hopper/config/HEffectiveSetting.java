package org.hopper.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** One effective setting value with schema metadata for the admin API. */
public final class HEffectiveSetting {

  private final String key;
  private final HSettingCategory category;
  private final HSettingType type;
  private final String value;
  private final HSettingSource source;
  private final boolean sensitive;
  private final boolean readOnly;
  private final boolean restartRequired;
  private final boolean configured;
  private final String description;
  private final String defaultValue;

  public HEffectiveSetting(
      String key,
      HSettingCategory category,
      HSettingType type,
      String value,
      HSettingSource source,
      boolean sensitive,
      boolean readOnly,
      boolean restartRequired,
      boolean configured,
      String description,
      String defaultValue) {
    this.key = key;
    this.category = category;
    this.type = type;
    this.value = value;
    this.source = source;
    this.sensitive = sensitive;
    this.readOnly = readOnly;
    this.restartRequired = restartRequired;
    this.configured = configured;
    this.description = description;
    this.defaultValue = defaultValue;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("category", category != null ? category.name() : null);
    m.put("type", type != null ? type.name() : null);
    m.put("value", value);
    m.put("source", source != null ? source.name() : null);
    m.put("sensitive", sensitive);
    m.put("readOnly", readOnly);
    m.put("restartRequired", restartRequired);
    m.put("configured", configured);
    m.put("description", description);
    m.put("defaultValue", defaultValue);
    return m;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public HSettingSource getSource() {
    return source;
  }

  public boolean isSensitive() {
    return sensitive;
  }

  public boolean isConfigured() {
    return configured;
  }

  public boolean isRestartRequired() {
    return restartRequired;
  }

  public boolean isReadOnly() {
    return readOnly;
  }
}
