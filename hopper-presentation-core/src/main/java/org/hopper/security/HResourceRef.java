package org.hopper.security;

import java.util.Objects;
import lombok.Getter;

/** Reference to a named resource for authorization checks and audit. */
@Getter
public final class HResourceRef {
  private final HResourceType type;
  private final String name;

  public HResourceRef(HResourceType type, String name) {
    this.type = Objects.requireNonNull(type, "type");
    this.name = name;
  }

  public static HResourceRef of(HResourceType type, String name) {
    return new HResourceRef(type, name);
  }

  public static HResourceRef presentation(String name) {
    return of(HResourceType.PRESENTATION, name);
  }

  public static HResourceRef connector(String name) {
    return of(HResourceType.CONNECTOR, name);
  }

  public static HResourceRef connection(String name) {
    return of(HResourceType.CONNECTION, name);
  }

  @Override
  public String toString() {
    return type + ":" + (name == null ? "" : name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HResourceRef that)) {
      return false;
    }
    return type == that.type && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, name);
  }
}
