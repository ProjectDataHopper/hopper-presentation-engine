/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.presentation.interaction;

import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/**
 * How a user triggers an interaction on a presentation (click, double-click, or hover).
 *
 * <p>Wire value is the enum {@linkplain #getCode() code} (e.g. {@code SINGLE_CLICK}).
 */
public enum HInteractionMethod implements IEnumHasCodeAndDescription {
  SINGLE_CLICK("Single click"),
  DOUBLE_CLICK("Double click"),
  MOUSE_HOVER("Mouse hover");

  private final String description;

  HInteractionMethod(String description) {
    this.description = description;
  }

  @Override
  public String getCode() {
    return name();
  }

  @Override
  public String getDescription() {
    return description;
  }

  public boolean isHover() {
    return this == MOUSE_HOVER;
  }

  public boolean isSingleClick() {
    return this == SINGLE_CLICK;
  }

  public boolean isDoubleClick() {
    return this == DOUBLE_CLICK;
  }

  /** Click methods that open/navigate (not hover). */
  public boolean isClick() {
    return this == SINGLE_CLICK || this == DOUBLE_CLICK;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(HInteractionMethod.class);
  }

  public static HInteractionMethod lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        HInteractionMethod.class, description, SINGLE_CLICK);
  }

  public static HInteractionMethod lookupCode(String code) {
    return org.apache.hop.metadata.api.IEnumHasCode.lookupCode(
        HInteractionMethod.class, code, SINGLE_CLICK);
  }

  /**
   * Parse a wire/API value (enum name or code). Blank/null → {@link #SINGLE_CLICK}.
   */
  public static HInteractionMethod fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return SINGLE_CLICK;
    }
    String v = raw.trim();
    for (HInteractionMethod m : values()) {
      if (m.name().equalsIgnoreCase(v) || m.getCode().equalsIgnoreCase(v)) {
        return m;
      }
    }
    // Friendly aliases
    if ("click".equalsIgnoreCase(v) || "single".equalsIgnoreCase(v)) {
      return SINGLE_CLICK;
    }
    if ("double".equalsIgnoreCase(v) || "dblclick".equalsIgnoreCase(v)) {
      return DOUBLE_CLICK;
    }
    if ("hover".equalsIgnoreCase(v) || "mouseover".equalsIgnoreCase(v)) {
      return MOUSE_HOVER;
    }
    return SINGLE_CLICK;
  }
}
