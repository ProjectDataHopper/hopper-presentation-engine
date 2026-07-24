package org.hopper.config;

/** Where an effective setting value came from in the merge stack. */
public enum HSettingSource {
  DEFAULT,
  BOOTSTRAP,
  OVERRIDE,
  ENV
}
