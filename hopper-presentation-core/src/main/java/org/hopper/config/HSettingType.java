package org.hopper.config;

/** Value type for a setting definition (drives validation and UI controls). */
public enum HSettingType {
  BOOLEAN,
  INT,
  STRING,
  ENUM,
  STRING_LIST,
  /** Stored as {@code ${ENV_VAR}} reference; never display resolved secret. */
  SECRET_REF
}
