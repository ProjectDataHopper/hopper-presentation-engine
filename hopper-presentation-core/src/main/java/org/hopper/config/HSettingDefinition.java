package org.hopper.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Schema entry for one admin-configurable property key. */
public final class HSettingDefinition {

  private final String key;
  private final HSettingCategory category;
  private final HSettingType type;
  private final String defaultValue;
  private final String description;
  private final boolean restartRequired;
  private final boolean sensitive;
  private final boolean readOnly;
  private final List<String> enumValues;
  private final Integer min;
  private final Integer max;

  private HSettingDefinition(Builder b) {
    this.key = Objects.requireNonNull(b.key, "key");
    this.category = Objects.requireNonNull(b.category, "category");
    this.type = Objects.requireNonNull(b.type, "type");
    this.defaultValue = b.defaultValue != null ? b.defaultValue : "";
    this.description = b.description != null ? b.description : "";
    this.restartRequired = b.restartRequired;
    this.sensitive = b.sensitive;
    this.readOnly = b.readOnly;
    this.enumValues =
        b.enumValues != null
            ? Collections.unmodifiableList(List.copyOf(b.enumValues))
            : List.of();
    this.min = b.min;
    this.max = b.max;
  }

  public static Builder builder(String key) {
    return new Builder(key);
  }

  public String getKey() {
    return key;
  }

  public HSettingCategory getCategory() {
    return category;
  }

  public HSettingType getType() {
    return type;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public String getDescription() {
    return description;
  }

  public boolean isRestartRequired() {
    return restartRequired;
  }

  public boolean isSensitive() {
    return sensitive;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public List<String> getEnumValues() {
    return enumValues;
  }

  public Integer getMin() {
    return min;
  }

  public Integer getMax() {
    return max;
  }

  public static final class Builder {
    private final String key;
    private HSettingCategory category = HSettingCategory.SERVER;
    private HSettingType type = HSettingType.STRING;
    private String defaultValue = "";
    private String description = "";
    private boolean restartRequired;
    private boolean sensitive;
    private boolean readOnly;
    private List<String> enumValues;
    private Integer min;
    private Integer max;

    private Builder(String key) {
      this.key = key;
    }

    public Builder category(HSettingCategory category) {
      this.category = category;
      return this;
    }

    public Builder type(HSettingType type) {
      this.type = type;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder restartRequired(boolean restartRequired) {
      this.restartRequired = restartRequired;
      return this;
    }

    public Builder sensitive(boolean sensitive) {
      this.sensitive = sensitive;
      return this;
    }

    public Builder readOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return this;
    }

    public Builder enumValues(String... values) {
      this.enumValues = values != null ? List.of(values) : List.of();
      return this;
    }

    public Builder min(Integer min) {
      this.min = min;
      return this;
    }

    public Builder max(Integer max) {
      this.max = max;
      return this;
    }

    public HSettingDefinition build() {
      return new HSettingDefinition(this);
    }
  }
}
